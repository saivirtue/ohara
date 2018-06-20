package com.island.ohara.configurator.data

import com.island.ohara.config.{OharaConfig, OharaProperty}

/**
  * a pojo to represent the description of ohara target
  * @param config stores all properties
  */
class OharaTarget(config: OharaConfig) extends OharaData(config) {

  override protected def extraProperties: Seq[OharaProperty[_]] = OharaTarget.properties

  def configs: Map[String, String] = OharaTarget.configProperty.require(config)

  override def copy[T](prop: OharaProperty[T], value: T): OharaTarget = {
    val clone = config.snapshot
    prop.set(clone, value)
    new OharaTarget(clone)
  }
}

object OharaTarget {

  /**
    * create a OharaTarget with specified config
    * @param config config
    * @return a new OharaTarget
    */
  def apply(config: OharaConfig) = new OharaTarget(config)

  /**
    * create an new OharaTarget with specified arguments
    * @param uuid uuid
    * @param name target name
    * @param configs configuration
    * @return an new OharaTarget
    */
  def apply(uuid: String, name: String, configs: Map[String, String]): OharaTarget = {
    val oharaConfig = OharaConfig()
    OharaData.uuidProperty.set(oharaConfig, uuid)
    OharaData.nameProperty.set(oharaConfig, name)
    configProperty.set(oharaConfig, configs)
    new OharaTarget(oharaConfig)
  }
  def properties: Seq[OharaProperty[_]] = Array(configProperty)
  val configProperty: OharaProperty[Map[String, String]] =
    OharaProperty.builder
      .key("ohara-target-config")
      .alias("config")
      .description("the configs of ohara target")
      .mapProperty
}
