/*
 * Copyright 2019 ABSA Group Limited
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

package ai.rubik.abris.avro.sql

import ai.rubik.metis.commons.SchemaUtils
import org.apache.avro.{Schema, SchemaBuilder}
import org.apache.spark.sql.avro.SchemaConverters.toAvroType
import org.apache.spark.sql.catalyst.expressions.Expression

/**
 * This class encapsulate logic and data necessary to get an Avro Schema from various sources
 *
 * Abris library doesn't support serialization of primitive types, therefore if there is only one primitive type
 * it must be internally wrapped in record before it is serialized and the schema must be changed to match.
 * The binary avro format for wrapped and bare type is identical, therefore this shouldn't cause any problems
 * on the other end.
 *
 * @param schemaGenerator function that generates schema
 */
class SchemaProvider private(private val schemaGenerator: Expression => (Schema, Schema))
  extends Serializable {

  @transient private var cachedOriginalSchema: Schema = _
  @transient private var cachedWrappedSchema: Schema = _

  private def lazyLoadSchemas(expression: Expression): Unit = {
    if (cachedOriginalSchema == null) {
      val schemas = schemaGenerator(expression)
      cachedOriginalSchema = schemas._1
      cachedWrappedSchema = schemas._2
    }
  }

  /**
   *
   * @param expression catalyst expression
   * @return unwrapped schema (it can be just one type without any record)
   */
  def originalSchema(expression: Expression): Schema = {
    lazyLoadSchemas(expression)
    cachedOriginalSchema
  }

  /**
   *
   * @param expression catalyst expression
   * @return wrapped schema (always record, even if it contains only one primitive type)
   */
  def wrappedSchema(expression: Expression): Schema = {
    lazyLoadSchemas(expression)
    cachedWrappedSchema
  }
}

object SchemaProvider {

  val DEFAULT_SCHEMA_NAME = "defaultName"
  val DEFAULT_SCHEMA_NAMESPACE = "defaultNamespace"

  def apply(schemaString: String): SchemaProvider = {

    new SchemaProvider((_: Expression) => {
      val schema = new Schema.Parser().parse(schemaString)
      (schema, wrapSchema(schema, DEFAULT_SCHEMA_NAME, DEFAULT_SCHEMA_NAMESPACE))
    })
  }

  def apply(): SchemaProvider = {
    apply(None, None)
  }

  def apply(name: Option[String], namespace: Option[String]): SchemaProvider = {

    new SchemaProvider((expression: Expression) => {
      val schemaName = name.getOrElse(DEFAULT_SCHEMA_NAME)
      val schemaNamespace = namespace.getOrElse(DEFAULT_SCHEMA_NAMESPACE)

      val schema = toAvroType(expression.dataType, expression.nullable, schemaName, schemaNamespace)
      // Metis - Massage schema in order to keep backward compatibility in place.
      val massagedSchema = SchemaUtils.massageSchema(schema)
      (massagedSchema, wrapSchema(massagedSchema, schemaName, schemaNamespace))
    })
  }

  private def wrapSchema(schema: Schema, name: String, namespace: String) = {
    if (schema.getType == Schema.Type.RECORD) {
      schema
    } else {
      SchemaBuilder.record(name)
        .namespace(namespace)
        .fields().name(schema.getName).`type`(schema).noDefault()
        .endRecord()
    }
  }
}
