package com.island.ohara.configurator.job

import com.island.ohara.config.{OharaConfig, OharaProperty, UuidUtil}
import com.island.ohara.configurator.data.OharaData
import com.island.ohara.serialization.DataType

class HttpJobRequest(oharaConfig: OharaConfig) extends OharaData(oharaConfig) {

  override def copy[T](prop: OharaProperty[T], value: T): HttpJobRequest = {
    val clone = oharaConfig.snapshot
    prop.set(clone, value)
    new HttpJobRequest(clone)
  }

  def path: String = HttpJobRequest.pathProperty.require(oharaConfig)

  /**
    * @return action
    */
  def action: Action = HttpJobRequest.actionProperty.require(oharaConfig)

  /**
    * @return schema
    */
  def schema: Map[String, DataType] = HttpJobRequest.schemaProperty.require(oharaConfig)

  /**
    * @return config
    */
  def config: Map[String, String] = HttpJobRequest.configProperty.require(oharaConfig)
  override protected def extraProperties: Seq[OharaProperty[_]] = HttpJobRequest.properties
}

object HttpJobRequest {

  def apply(action: Action, path: String, schema: Map[String, DataType], config: Map[String, String]): HttpJobRequest =
    apply(UuidUtil.uuid(), classOf[HttpJobRequest].getSimpleName, action, path, schema, config)

  def apply(uuid: String,
            name: String,
            action: Action,
            path: String,
            schema: Map[String, DataType],
            config: Map[String, String]): HttpJobRequest = {
    val oharaConfig = OharaConfig()
    OharaData.uuidProperty.set(oharaConfig, uuid)
    OharaData.nameProperty.set(oharaConfig, name)
    actionProperty.set(oharaConfig, action)
    pathProperty.set(oharaConfig, path)
    schemaProperty.set(oharaConfig, schema)
    configProperty.set(oharaConfig, config)
    new HttpJobRequest(oharaConfig)
  }

  def properties: Seq[OharaProperty[_]] = Array(pathProperty, actionProperty, configProperty, schemaProperty)
  val pathProperty: OharaProperty[String] =
    OharaProperty.builder
      .key("http-job-request-path")
      .alias("path")
      .description("the path of HttpJobRequest")
      .stringProperty
  val actionProperty: OharaProperty[Action] = OharaProperty.builder
    .key("http-job-request-action")
    .alias("action")
    .description("the action of HttpJobRequest")
    .property(Action.of(_), _.name)
  val schemaProperty: OharaProperty[Map[String, DataType]] = OharaProperty.builder
    .key("http-job-request-schema")
    .alias("schema")
    .description("the schema of HttpJobRequest")
    .mapProperty(DataType.of(_), _.name)

  val configProperty: OharaProperty[Map[String, String]] =
    OharaProperty.builder
      .key("http-job-request-config")
      .alias("config")
      .description("the config of HttpJobRequest")
      .mapProperty
}
