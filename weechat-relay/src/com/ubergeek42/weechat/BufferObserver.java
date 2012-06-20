/*******************************************************************************
 * Copyright 2012 Keith Johnson
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.ubergeek42.weechat;

/**
 * Allows objects to receive notifications when certain aspects of a buffer change
 * @author ubergeek42<kj@ubergeek42.com>
 *
 */
public interface BufferObserver {
	/**
	 * Called when a line is added to a buffer
	 */
	public void onLineAdded();
	
	/**
	 * Called when the buffer is closed in weechat
	 */
	public void onBufferClosed();
	
	/**
	 * Called when the nicklist changes
	 */
	public void onNicklistChanged();
}
