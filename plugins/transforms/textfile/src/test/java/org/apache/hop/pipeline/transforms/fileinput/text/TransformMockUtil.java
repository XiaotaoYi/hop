/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hop.pipeline.transforms.fileinput.text;

import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.ITransformData;
import org.apache.hop.pipeline.transform.ITransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.mock.TransformMockHelper;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

/**
 * <p>
 * Util class to handle TransformMock creation in generic way.
 * </p>
 * <p>
 * Usage example:
 * <pre>
 * Mapping transform = TransformMockUtil.getTransform( Mapping.class, MappingMeta.class, "junit" );
 * </pre>
 *
 *
 * </p>
 */
public class TransformMockUtil {

  public static <Meta extends ITransformMeta, Main extends BaseTransform, Data extends ITransformData> TransformMockHelper<Meta, Data> getTransformMockHelper( Class<Meta> meta, Class<Data> data, String name ) {
    TransformMockHelper<Meta, Data> transformMockHelper = new TransformMockHelper<>( name, meta, data );
    when( transformMockHelper.logChannelFactory.create( any(), any( ILoggingObject.class ) ) ).thenReturn( transformMockHelper.iLogChannel );
    when( transformMockHelper.logChannelFactory.create( any() ) ).thenReturn( transformMockHelper.iLogChannel );
    when( transformMockHelper.pipeline.isRunning() ).thenReturn( true );
    return transformMockHelper;
  }

  public static <Main extends BaseTransform, Meta extends ITransformMeta, Data extends ITransformData> Main getTransform( Class<Main> mainClass, Class<Meta> metaClass, Class<Data> dataClass, TransformMockHelper<Meta, Data> mock )
    throws NoSuchMethodException, SecurityException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    Constructor<Main> kons = mainClass.getConstructor( TransformMeta.class, metaClass, dataClass, int.class, PipelineMeta.class, Pipeline.class );
    Main transform = kons.newInstance( mock.transformMeta, mock.iTransformMeta, mock.iTransformData, 0, mock.pipelineMeta, mock.pipeline );
    return transform;
  }


  public static <Main extends BaseTransform, Meta extends ITransformMeta, Data extends ITransformData> Main getTransform( Class<Main> mainClass, Meta meta, Data data, Class<Meta> metaClass, Class<Data> dataClass, String transformName )
    throws NoSuchMethodException, SecurityException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {

    TransformMockHelper<Meta, Data> transformMockHelper = TransformMockUtil.getTransformMockHelper( metaClass, dataClass, transformName );

    Constructor<Main> kons = mainClass.getConstructor( TransformMeta.class, metaClass, dataClass, int.class, PipelineMeta.class, Pipeline.class );
    Main main = kons.newInstance( transformMockHelper.transformMeta, meta, data, 0, transformMockHelper.pipelineMeta, transformMockHelper.pipeline );

    return main;
  }
}
