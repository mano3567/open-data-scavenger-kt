package se.metricspace.opendata

import java.time.Instant
import kotlin.math.*

// Hjälpfunktion för äkta matematisk modulo (hanterar negativa tal korrekt)
fun Double.floorMod(modulus: Double): Double {
    return ((this % modulus) + modulus) % modulus
}

data class HorizontalCoordinates(val altitudeDegrees: Double, val azimuthDegrees: Double)

fun calculateStarPosition(
    starRaHours: Double,   // Stjärnans RA i timmar (från katalogen)
    starDecDegrees: Double, // Stjärnans Dec i grader (från katalogen)
    observerLat: Double,    // Din latitud (t.ex. 59.32)
    observerLon: Double     // Din longitud (t.ex. 18.06)
): HorizontalCoordinates {

    // 1. Hämta nuvarande tid i UTC och konvertera till Julianskt Datum (JD)
    val now = Instant.now()
    val unixSeconds = now.epochSecond + (now.nano / 1_000_000_000.0)
    val jd = 2440587.5 + (unixSeconds / 86400.0)

    // 2. Beräkna tiden i dagar sedan standardepoken J2000.0
    val d = jd - 2451545.0

    // 3. Beräkna GMST (Greenwich Mean Sidereal Time) i timmar
    var gmst = 18.697374558 + 24.06570982441908 * d
    gmst = gmst.floorMod(24.0)

    // 4. Beräkna LST (Local Sidereal Time) i timmar för din longitud
    // Eftersom jorden snurrar 15 grader i timmen, delar vi longituden med 15
    var lst = gmst + (observerLon / 15.0)
    lst = lst.floorMod(24.0)

    // 5. Beräkna Timvinkeln (Hour Angle, HA)
    var haHours = lst - starRaHours
    haHours = haHours.floorMod(24.0)

    // --- NU GÅR VI ÖVER TILL SFÄRISK TRIGONOMETRI ---

    // Konvertera allt till radianer för Kotlins math-funktioner
    val latRad = observerLat * (PI / 180.0)
    val decRad = starDecDegrees * (PI / 180.0)
    // 1 timme = 15 grader. Konvertera timmar till grader, sedan till radianer.
    val haRad = (haHours * 15.0) * (PI / 180.0)

    // 6. Beräkna Altitud (Höjd)
    val sinAlt = sin(latRad) * sin(decRad) + cos(latRad) * cos(decRad) * cos(haRad)
    val altRad = asin(sinAlt)
    val altDegrees = altRad * (180.0 / PI)

    // 7. Beräkna Azimut (Väderstreck)
    val y = -sin(haRad) * cos(decRad)
    val x = sin(decRad) * cos(latRad) - cos(decRad) * sin(latRad) * cos(haRad)

    val azRad = atan2(y, x)
    val azDegrees = azRad * (180.0 / PI)

    // atan2 kan ge negativa vinklar, vi tvingar den till 0-360 grader
    val finalAzDegrees = azDegrees.floorMod(360.0)

    return HorizontalCoordinates(altDegrees, finalAzDegrees)
}