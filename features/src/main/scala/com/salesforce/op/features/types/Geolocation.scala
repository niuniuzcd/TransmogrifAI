/*
 * Copyright (c) 2017, Salesforce.com, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.salesforce.op.features.types

import language.postfixOps
import org.apache.lucene.geo.GeoUtils
import enumeratum.values.{IntEnum, IntEnumEntry}
import org.apache.lucene.spatial3d.geom.{GeoPoint, PlanetModel}
import Geolocation._

import scala.util.Try

/**
 * Represented as a list of latitude, longitude, accuracy
 * The value is only populated if all are present, otherwise [[IllegalArgumentException]] is thrown.
 *
 * @param value a list of latitude, longitude, accuracy
 */
class Geolocation(val value: Seq[Double]) extends OPList[Double] with Location {
  require(isEmpty || value.length == 3, s"Geolocation must have lat, lon, and accuracy, or be empty: $value")
  if (!isEmpty) {
    Geolocation.validate(lat, lon)
    require(Try(accuracy).isSuccess, "Invalid accuracy value provided")
  }
  def this(lat: Double, lon: Double, accuracy: GeolocationAccuracy) = this(geolocationData(lat, lon, accuracy))
  def this(v: (Double, Double, Double)) = this(geolocationData(v._1, v._2, v._3))
  /**
   * Latitude value
   */
  def lat: Double = if (isEmpty) Double.NaN else value(0)
  /**
   * Longitude value
   */
  def lon: Double = if (isEmpty) Double.NaN else value(1)
  /**
   * Latitude value
   */
  def latitude: Double = lat
  /**
   * Longitude value
   */
  def longitude: Double = lon

  /**
   * Geolocation accuracy value [[GeolocationAccuracy]]
   */
  def accuracy: GeolocationAccuracy = {
    if (isEmpty) GeolocationAccuracy.Unknown else GeolocationAccuracy.withValue(value(2).toInt)
  }

  /**
   * Convert to [[GeoPoint]] value
   */
  def toGeoPoint: GeoPoint = {
    // If this Geolocation object is empty, then return the zero vector as the GeoPoint since we use
    // GeoPoint coordinates in aggregation functions
    if (isEmpty) Geolocation.EmptyGeoPoint
    else new GeoPoint(PlanetModel.WGS84, math.toRadians(lat), math.toRadians(lon))
  }
  override def toString: String = {
    val vals = if (nonEmpty) f"$lat%.5f, $lon%.5f, $accuracy" else ""
    s"${getClass.getSimpleName}($vals)"
  }
}
/**
 * Represented as a list of latitude, longitude, accuracy (only populated if all are present)
 */
object Geolocation {
  private[types] def geolocationData(
    lat: Double,
    lon: Double,
    accuracy: GeolocationAccuracy
  ): Seq[Double] = geolocationData(lat, lon, accuracy.value)

  private[types] def geolocationData(
    lat: Double,
    lon: Double,
    accuracy: Double): Seq[Double] = {
    if (lat.isNaN || lon.isNaN) List[Double]() else List(lat, lon, accuracy)
  }

  def apply(lat: Double, lon: Double, accuracy: GeolocationAccuracy): Geolocation =
    new Geolocation(lat = lat, lon = lon, accuracy = accuracy)
  def apply(v: Seq[Double]): Geolocation = new Geolocation(v)
  def apply(v: (Double, Double, Double)): Geolocation = new Geolocation(v)
  def empty: Geolocation = FeatureTypeDefaults.Geolocation
  private val EmptyGeoPoint = new GeoPoint(0.0, 0.0, 0.0)

  def validate(lat: Double, lon: Double): Unit = {
    GeoUtils.checkLatitude(lat)
    GeoUtils.checkLongitude(lon)
  }
  val EquatorInMiles = 24901.0
  val EarthRadius = 3959.0
  val Names = Seq("latitude", "longitude", "accuracy")
}

/**
 * Geolocation Accuracy tells you more about the location at the latitude and longitude for a give address.
 * For example, 'Zip' means the latitude and longitude point to the center of the zip code area.
 */
sealed abstract class GeolocationAccuracy
(
  val value: Int,
  val name: String,
  val rangeInMiles: Double
) extends IntEnumEntry {
  /**
   * Range in units of Earth Radius
   */
  def rangeInUnits: Double = rangeInMiles / EarthRadius
}

case object GeolocationAccuracy extends IntEnum[GeolocationAccuracy] {
  val values: List[GeolocationAccuracy] = findValues.toList sortBy(_.rangeInMiles)


  // No match for the address was found
  case object Unknown extends GeolocationAccuracy(0, name = "Unknown", rangeInMiles = EquatorInMiles / 2)
  // In the same building
  case object Address extends GeolocationAccuracy(1, name = "Address", rangeInMiles = 0.005)
  // Near the address
  case object NearAddress extends GeolocationAccuracy(2, name = "NearAddress", rangeInMiles = 0.02)
  // Midway point of the block
  case object Block extends GeolocationAccuracy(3, name = "Block", rangeInMiles = 0.05)
  // Midway point of the street
  case object Street extends GeolocationAccuracy(4, name = "Street", rangeInMiles = 0.15)
  // Center of the extended zip code area
  case object ExtendedZip extends GeolocationAccuracy(5, name = "ExtendedZip", rangeInMiles = 0.4)
  // Center of the zip code area
  case object Zip extends GeolocationAccuracy(6, name = "Zip", rangeInMiles = 1.2)
  // Center of the neighborhood
  case object Neighborhood extends GeolocationAccuracy(7, name = "Neighborhood", rangeInMiles = 3.0)
  // Center of the city
  case object City extends GeolocationAccuracy(8, name = "City", rangeInMiles = 12.0)
  // Center of the county
  case object County extends GeolocationAccuracy(9, name = "County", rangeInMiles = 40.0)
  // Center of the state
  case object State extends GeolocationAccuracy(10, name = "State", rangeInMiles = 150.0)

  /**
   * Convert units of Earth Radius into miles
   *
   * @param u units of Earth Radius
   * @return miles
   */
  def geoUnitsToMiles(u: Double): Double = u * EarthRadius

  /**
   * Construct accuracy value for a given range in miles
   *
   * @param miles range in miles
   * @return accuracy
   */
  def forRangeInMiles(miles: Double): GeolocationAccuracy = {
    val result = values.dropWhile(v => v.rangeInMiles < miles * 0.99).headOption getOrElse Unknown
    result
  }

  /**
   * Construct accuracy value for a given range in units of Earth Radius
   *
   * @param units units of Earth Radius
   * @return accuracy
   */
  def forRangeInUnits(units: Double): GeolocationAccuracy =
    forRangeInMiles(geoUnitsToMiles(units))

  /**
   * Find the worst accuracy value
   *
   * @param accuracies list of accuracies
   * @return worst accuracy
   */
  def worst(accuracies: GeolocationAccuracy*): GeolocationAccuracy = {
    forRangeInMiles((Unknown :: accuracies.toList) map (_.rangeInMiles) max)
  }
}
