/*! ******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2017 by Hitachi Vantara : http://www.pentaho.com
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

package org.apache.hop.job.entry;

import org.apache.hop.metastore.api.IMetaStore;

/**
 * IJobEntryDialog is the Java interface that implements the settings dialog of a job entry plugin. The
 * responsibilities of the implementing class are listed below.<br/>
 * <br/>
 * <ul>
 * <li><a href="#open()">public IJobEntry open()</a></li>
 * </ul>
 * <br/>
 * This method should return only after the dialog has been confirmed or cancelled. The method must conform to the
 * following rules:<br/>
 * <br/>
 * If the dialog is confirmed:<br/>
 * <ul>
 * <li>The IJobEntry object must be updated to reflect the new settings</li>
 * <li>If the user changed any settings, the IJobEntry object's "changed" flag must be set to true</li>
 * <li>open() must return the IJobEntry object</li>
 * </ul>
 * <br/>
 * If the dialog is cancelled:<br/>
 * <ul>
 * <li>The IJobEntry object must not be changed</li>
 * <li>The IJobEntry object's "changed" flag must be set to the value it had at the time the dialog opened</li>
 * <li>open() must return null</li>
 * </ul>
 *
 * @author Matt
 * @since 29-okt-2004
 */
public interface IJobEntryDialog {

  /**
   * Opens a JobEntryDialog and waits for the dialog to be confirmed or cancelled.
   *
   * @return the job entry interface if the dialog is confirmed, null otherwise
   */
  IJobEntry open();

  /**
   * The MetaStore to pass
   *
   * @param metaStore
   */
  void setMetaStore( IMetaStore metaStore );
}