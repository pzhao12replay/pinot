/**
 * Copyright (C) 2014-2015 LinkedIn Corp. (pinot-core@linkedin.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.linkedin.thirdeye.hadoop.segment.creation;

public enum SegmentCreationPhaseConstants {

  SEGMENT_CREATION_SCHEMA_PATH("segment.creation.schema.path"),
  SEGMENT_CREATION_INPUT_PATH("segment.creation.input.path"),
  SEGMENT_CREATION_OUTPUT_PATH("segment.creation.output.path"),
  SEGMENT_CREATION_CONFIG_PATH("segment.creation.config.path"),
  SEGMENT_CREATION_SEGMENT_TABLE_NAME("segment.creation.segment.table.name"),
  SEGMENT_CREATION_DATA_SCHEMA("segment.create.data.schema"),
  SEGMENT_CREATION_THIRDEYE_CONFIG("segment.create.thirdeye.config"),
  SEGMENT_CREATION_WALLCLOCK_START_TIME("segment.create.wallclock.start.time"),
  SEGMENT_CREATION_WALLCLOCK_END_TIME("segment.create.wallclock.end.time"),
  SEGMENT_CREATION_SCHEDULE("segment.creation.schedule");

  String name;

  SegmentCreationPhaseConstants(String name) {
    this.name = name;
  }

  public String toString() {
    return name;
  }

}