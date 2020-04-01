/*! ******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2019 by Hitachi Vantara : http://www.pentaho.com
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package org.apache.hop.pipeline.steps.singlethreader;

import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopStepException;
import org.apache.hop.core.logging.HopLogStore;
import org.apache.hop.core.row.RowMetaInterface;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.SingleThreadedPipelineExecutor;
import org.apache.hop.pipeline.StepWithMappingMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.PipelineMeta.PipelineType;
import org.apache.hop.pipeline.step.BaseStep;
import org.apache.hop.pipeline.step.RowAdapter;
import org.apache.hop.pipeline.step.StepDataInterface;
import org.apache.hop.pipeline.step.StepInterface;
import org.apache.hop.pipeline.step.StepMeta;
import org.apache.hop.pipeline.step.StepMetaInterface;
import org.apache.hop.pipeline.steps.PipelineStepUtil;
import org.apache.hop.pipeline.steps.mapping.MappingValueRename;
import org.apache.hop.pipeline.steps.mappinginput.MappingInputData;

import java.util.ArrayList;

/**
 * Execute a mapping: a re-usuable pipeline
 *
 * @author Matt
 * @since 22-nov-2005
 */
public class SingleThreader extends BaseStep implements StepInterface {
  private static Class<?> PKG = SingleThreaderMeta.class; // for i18n purposes, needed by Translator!!

  private SingleThreaderMeta meta;
  private SingleThreaderData data;

  public SingleThreader( StepMeta stepMeta, StepDataInterface stepDataInterface, int copyNr, PipelineMeta pipelineMeta,
                         Pipeline pipeline ) {
    super( stepMeta, stepDataInterface, copyNr, pipelineMeta, pipeline );
  }

  /**
   * Process rows in batches of N rows. The sub- pipeline will execute in a single thread.
   */
  public boolean processRow( StepMetaInterface smi, StepDataInterface sdi ) throws HopException {
    meta = (SingleThreaderMeta) smi;
    setData( (SingleThreaderData) sdi );
    SingleThreaderData singleThreaderData = getData();
    Object[] row = getRow();
    if ( row == null ) {
      if ( singleThreaderData.batchCount > 0 ) {
        singleThreaderData.batchCount = 0;
        return execOneIteration();
      }

      setOutputDone();
      return false;
    }
    if ( first ) {
      first = false;
      singleThreaderData.startTime = System.currentTimeMillis();
    }

    // Add the row to the producer...
    //
    singleThreaderData.rowProducer.putRow( getInputRowMeta(), row );
    singleThreaderData.batchCount++;

    if ( getStepMeta().isDoingErrorHandling() ) {
      singleThreaderData.errorBuffer.add( row );
    }

    boolean countWindow = singleThreaderData.batchSize > 0 && singleThreaderData.batchCount >= singleThreaderData.batchSize;
    boolean timeWindow = singleThreaderData.batchTime > 0 && ( System.currentTimeMillis() - singleThreaderData.startTime ) > singleThreaderData.batchTime;

    if ( countWindow || timeWindow ) {
      singleThreaderData.batchCount = 0;
      boolean more = execOneIteration();
      if ( !more ) {
        setOutputDone();
        return false;
      }
      singleThreaderData.startTime = System.currentTimeMillis();
    }
    return true;
  }

  private boolean execOneIteration() {
    boolean more = false;
    try {
      more = getData().executor.oneIteration();
      if ( getData().executor.isStopped() || getData().executor.getErrors() > 0 ) {
        return handleError();
      }
    } catch ( Exception e ) {
      setErrors( 1L );
      stopAll();
      logError( BaseMessages.getString( PKG, "SingleThreader.Log.ErrorOccurredInSubPipeline" ) );
      return false;
    } finally {
      if ( getStepMeta().isDoingErrorHandling() ) {
        getData().errorBuffer.clear();
      }
    }
    return more;
  }

  private boolean handleError() throws HopStepException {
    SingleThreaderData singleThreaderData = getData();
    if ( getStepMeta().isDoingErrorHandling() ) {
      int lastLogLine = HopLogStore.getLastBufferLineNr();
      StringBuffer logText =
        HopLogStore.getAppender().getBuffer( singleThreaderData.mappingPipeline.getLogChannelId(), false, singleThreaderData.lastLogLine );
      singleThreaderData.lastLogLine = lastLogLine;

      for ( Object[] row : singleThreaderData.errorBuffer ) {
        putError( getInputRowMeta(), row, 1L, logText.toString(), null, "STR-001" );
      }

      singleThreaderData.executor.clearError();

      return true; // continue
    } else {
      setErrors( 1 );
      stopAll();
      logError( BaseMessages.getString( PKG, "SingleThreader.Log.ErrorOccurredInSubPipeline" ) );
      return false; // stop running
    }
  }

  public void prepareMappingExecution() throws HopException {
    SingleThreaderData singleThreaderData = getData();
    // Set the type to single threaded in case the user forgot...
    //
    singleThreaderData.mappingPipelineMeta.setPipelineType( PipelineType.SingleThreaded );

    // Create the pipeline from meta-data...
    singleThreaderData.mappingPipeline = new Pipeline( singleThreaderData.mappingPipelineMeta, getPipeline() );

    // Pass the parameters down to the sub-pipeline.
    //
    StepWithMappingMeta
      .activateParams( getData().mappingPipeline, getData().mappingPipeline, this, getData().mappingPipeline.listParameters(),
        meta.getParameters(), meta.getParameterValues(), meta.isPassingAllParameters() );
    getData().mappingPipeline.activateParameters();

    // Disable thread priority managment as it will slow things down needlessly.
    // The single threaded engine doesn't use threads and doesn't need row locking.
    //
    singleThreaderData.mappingPipeline.getPipelineMeta().setUsingThreadPriorityManagment( false );

    // Leave a path up so that we can set variables in sub-pipelines...
    //
    singleThreaderData.mappingPipeline.setParentPipeline( getPipeline() );

    // Pass down the safe mode flag to the mapping...
    //
    singleThreaderData.mappingPipeline.setSafeModeEnabled( getPipeline().isSafeModeEnabled() );

    // Pass down the metrics gathering flag to the mapping...
    //
    singleThreaderData.mappingPipeline.setGatheringMetrics( getPipeline().isGatheringMetrics() );

    // Also set the name of this step in the mapping pipeline for logging purposes
    //
    singleThreaderData.mappingPipeline.setMappingStepName( getStepname() );

    initServletConfig();

    // prepare the execution
    //
    singleThreaderData.mappingPipeline.prepareExecution();

    // If the inject step is a mapping input step, tell it all is OK...
    //
    if ( singleThreaderData.injectStepMeta.isMappingInput() ) {
      MappingInputData mappingInputData =
        (MappingInputData) singleThreaderData.mappingPipeline.findDataInterface( singleThreaderData.injectStepMeta.getName() );
      mappingInputData.sourceSteps = new StepInterface[ 0 ];
      mappingInputData.valueRenames = new ArrayList<MappingValueRename>();
    }

    // Add row producer & row listener
    singleThreaderData.rowProducer = singleThreaderData.mappingPipeline.addRowProducer( meta.getInjectStep(), 0 );

    StepInterface retrieveStep = singleThreaderData.mappingPipeline.getStepInterface( meta.getRetrieveStep(), 0 );
    retrieveStep.addRowListener( new RowAdapter() {
      @Override
      public void rowWrittenEvent( RowMetaInterface rowMeta, Object[] row ) throws HopStepException {
        // Simply pass it along to the next steps after the SingleThreader
        //
        SingleThreader.this.putRow( rowMeta, row );
      }
    } );

    singleThreaderData.mappingPipeline.startThreads();

    // Create the executor...
    singleThreaderData.executor = new SingleThreadedPipelineExecutor( singleThreaderData.mappingPipeline );

    // We launch the pipeline in the processRow when the first row is received.
    // This will allow the correct variables to be passed.
    // Otherwise the parent is the init() thread which will be gone once the init is done.
    //
    try {
      boolean ok = singleThreaderData.executor.init();
      if ( !ok ) {
        throw new HopException( BaseMessages.getString(
          PKG, "SingleThreader.Exception.UnableToInitSingleThreadedPipeline" ) );
      }
    } catch ( HopException e ) {
      throw new HopException( BaseMessages.getString(
        PKG, "SingleThreader.Exception.UnableToPrepareExecutionOfMapping" ), e );
    }

    // Add the mapping pipeline to the active sub-pipelines map in the parent pipeline
    //
    getPipeline().addActiveSubPipeline( getStepname(), singleThreaderData.mappingPipeline );
  }

  void initServletConfig() {
    PipelineStepUtil.initServletConfig( getPipeline(), getData().getMappingPipeline() );
  }

  public boolean init( StepMetaInterface smi, StepDataInterface sdi ) {
    meta = (SingleThreaderMeta) smi;
    setData( (SingleThreaderData) sdi );
    SingleThreaderData singleThreaderData = getData();
    if ( super.init( smi, sdi ) ) {
      // First we need to load the mapping (pipeline)
      try {
        // The batch size...
        singleThreaderData.batchSize = Const.toInt( environmentSubstitute( meta.getBatchSize() ), 0 );
        singleThreaderData.batchTime = Const.toInt( environmentSubstitute( meta.getBatchTime() ), 0 );

        // TODO: Pass the MetaStore down to the metadata object?...
        //
        singleThreaderData.mappingPipelineMeta = SingleThreaderMeta.loadSingleThreadedPipelineMeta( meta, this, meta.isPassingAllParameters() );
        if ( singleThreaderData.mappingPipelineMeta != null ) { // Do we have a mapping at all?

          // Validate the inject and retrieve step names
          //
          String injectStepName = environmentSubstitute( meta.getInjectStep() );
          singleThreaderData.injectStepMeta = singleThreaderData.mappingPipelineMeta.findStep( injectStepName );
          if ( singleThreaderData.injectStepMeta == null ) {
            logError( "The inject step with name '"
              + injectStepName + "' couldn't be found in the sub-pipeline" );
          }

          String retrieveStepName = environmentSubstitute( meta.getRetrieveStep() );
          if ( !Utils.isEmpty( retrieveStepName ) ) {
            singleThreaderData.retrieveStepMeta = singleThreaderData.mappingPipelineMeta.findStep( retrieveStepName );
            if ( singleThreaderData.retrieveStepMeta == null ) {
              logError( "The retrieve step with name '"
                + retrieveStepName + "' couldn't be found in the sub-pipeline" );
            }
          }

          // OK, now prepare the execution of the mapping.
          // This includes the allocation of RowSet buffers, the creation of the
          // sub- pipeline threads, etc.
          //
          prepareMappingExecution();

          if ( getStepMeta().isDoingErrorHandling() ) {
            singleThreaderData.errorBuffer = new ArrayList<Object[]>();
          }

          // That's all for now...
          //
          return true;
        } else {
          logError( "No valid mapping was specified!" );
          return false;
        }
      } catch ( Exception e ) {
        logError( "Unable to load the mapping pipeline because of an error : " + e.toString() );
        logError( Const.getStackTracker( e ) );
      }

    }
    return false;
  }

  public void dispose( StepMetaInterface smi, StepDataInterface sdi ) {
    // dispose of the single threading execution engine
    //
    try {
      if ( getData().executor != null ) {
        getData().executor.dispose();
      }
    } catch ( HopException e ) {
      log.logError( "Error disposing of sub-pipeline: ", e );
    }

    super.dispose( smi, sdi );
  }

  public void stopRunning( StepMetaInterface stepMetaInterface, StepDataInterface stepDataInterface ) throws HopException {
    if ( getData().mappingPipeline != null ) {
      getData().mappingPipeline.stopAll();
    }
  }

  public void stopAll() {
    // Stop the mapping step.
    if ( getData().mappingPipeline != null ) {
      getData().mappingPipeline.stopAll();
    }

    // Also stop this step
    super.stopAll();
  }

  public Pipeline getMappingPipeline() {
    return getData().mappingPipeline;
  }

  // Method is defined as package-protected in order to be accessible by unit tests
  SingleThreaderData getData() {
    return data;
  }

  private void setData( SingleThreaderData data ) {
    this.data = data;
  }
}