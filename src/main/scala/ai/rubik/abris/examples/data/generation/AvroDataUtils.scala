/*
 * Copyright 2018 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.rubik.abris.examples.data.generation

import java.io.ByteArrayOutputStream

import ai.rubik.abris.avro.parsing.utils.AvroSchemaUtils
import org.apache.avro.Schema
import org.apache.avro.generic.{GenericDatumReader, GenericRecord, GenericRecordBuilder}
import org.apache.avro.io.{DecoderFactory, EncoderFactory}
import ai.rubik.abris.avro.write.ScalaCustomDatumWriter

/**
 * This class provides utilities for coping with Avro data.
 * This is not part of the library core is used for example data generation only.
 */
object AvroDataUtils {

  def mapToGenericRecordDirectly(data: Map[String, Object], schema: String): GenericRecord = {
    val avroRecordBuilder = getRecordBuilder(schema)
    for (entry <- data.iterator) {
      avroRecordBuilder.set(entry._1, entry._2)
    }
    avroRecordBuilder.build()
  }

  def mapToGenericRecord(data: Map[String, Object], schema: String): GenericRecord = {
    // so that we have the proper data types added to each record field
    passRecordThroughAvroApi(mapToGenericRecordDirectly(data, schema))
  }

  private def getRecordBuilder(schema: String): GenericRecordBuilder = {
    val parsedSchema = AvroSchemaUtils.parse(schema)
    new GenericRecordBuilder(parsedSchema)
  }

  private def passRecordThroughAvroApi(avroRecord: GenericRecord): GenericRecord = {
    val recordBytes = recordToBytes(avroRecord)
    bytesToRecord(recordBytes, avroRecord.getSchema)
  }

  def recordToBytes(record: GenericRecord): Array[Byte] = {
    val writer = new ScalaCustomDatumWriter[GenericRecord]()
    writer.setSchema(record.getSchema)
    val outStream = new ByteArrayOutputStream()
    try {
      val encoder = EncoderFactory.get().binaryEncoder(outStream, null);
      writer.write(record, encoder);
      encoder.flush();
      outStream.toByteArray();
    } finally {
      outStream.close();
    }
  }

  def bytesToRecord(avroBytes: Array[Byte], schema: Schema): GenericRecord = {
    /*
    val reader: ScalaDatumReader[GenericRecord] = new ScalaDatumReader[GenericRecord](schema)
    val decoder = DecoderFactory.get().binaryDecoder(avroBytes, null)
    reader.read(null, decoder)
    */

    val decoder =  DecoderFactory.get().binaryDecoder(avroBytes, 0, avroBytes.length, null)
    val reader = new GenericDatumReader[Any](schema)

    reader.read(reader, decoder).asInstanceOf[GenericRecord]
  }
}
