package se.metricspace.opendata

import java.net.http.HttpClient
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

data class SignalInformation(
    val active: Boolean,
    val band: String,
    val dataRate: Int,
    val frequency: Int,
    val power: Double,
    val signalType: String,
    val spacecraft: String,
    val spacecraftID: Int
)

data class TargetInformation(
    val downlegRange: Long,
    val friendlyName: String,
    val id: Int,
    val name: String,
    val rtlt: Int,
    val uplegRange: Long
)

data class DishInformation(
    val activity: String,
    val azimuthAngle: Int,
    val elevationAngle: Int,
    val isArray: Boolean,
    val isDDOR: Boolean,
    val isMSPA: Boolean,
    val name: String,
    val windSpeed: Int,
    val downSignals: List<SignalInformation>,
    val upSignals: List<SignalInformation>,
    val targets: List<TargetInformation>
)

data class StationInformation(
    val dishes: List<DishInformation>,
    val friendlyName: String,
    val name: String,
    val timeUTC: Long
)

class DeepSpaceNetworkService(private val httpClient: HttpClient) {
    private val DSNURL = "https://eyes.nasa.gov/dsn/data/dsn.xml"

    private val spacecraftNames = mapOf(
        "ACE" to "ACE (Advanced Composition Explorer)",
        "CAPS" to "CAPSTONE",
        "DSCOVR" to "DSCOVR (Deep Space Climate Observatory)",
        "EUCLID" to "Euclid",
        "JWST"  to "James Webb Space Telescope",
        "JUICE" to "JUICE (Jupiter Icy Moons Explorer)",
        "LRO"  to "Lunar Reconnaissance Orbiter",
        "M20"  to "Perseverance (Mars 2020)",
        "MEX"  to "Mars Express",
        "MSL"  to "Curiosity (Mars Science Lab)",
        "MRO" to "Mars Reconnaissance Orbiter",
        "NHPC" to "New Horizons",
        "ODY" to "Mars Odyssey",
        "OSIRIS" to "OSIRIS-APEX",
        "PSP" to "Parker Solar Probe",
        "PSYCHE" to "Psyche",
        "ORX" to "OSIRIS-REx (nu OSIRIS-APEX)",
        "SDO" to "Solar Dynamics Observatory",
        "SOHO" to "SOHO (Solar and Heliospheric Observatory)",
        "SOLO" to "Solar Orbiter (ESA/NASA)",
        "STA" to "STEREO A",
        "TGO" to "ExoMars Trace Gas Orbiter",
        "VGR1" to "Voyager 1",
        "VGR2" to "Voyager 2",
        "WIND" to "WIND (Solvindsobservatorium)",
    )

    private fun String.toSpacecraftFriendlyName(): String {
        return spacecraftNames[this.uppercase()] ?: this
    }

    private fun getFacilityForDish(dishName: String): String {
        val dishNumber = dishName.removePrefix("DSS").toIntOrNull() ?: return "Okänd anläggning"
        return when (dishNumber) {
            in 10..29 -> "Goldstone (USA)"
            in 30..49 -> "Canberra (Australien)"
            in 50..69 -> "Madrid (Spanien)"
            else -> "Okänd anläggning"
        }
    }

    fun parseSignal(signalNode: Element): SignalInformation {
        val active = signalNode.getAttribute("active").toBoolean()
        val band = signalNode.getAttribute("band").toString()
        val dataRate = signalNode.getAttribute("dataRate").toIntOrNull() ?: 0
        val frequency = signalNode.getAttribute("frequency").toIntOrNull() ?: 0
        val power = signalNode.getAttribute("power").toDoubleOrNull() ?: 0.0
        val signalType = signalNode.getAttribute("signalType").toString()
        val spacecraft = signalNode.getAttribute("spacecraft").toString()
        val spacecraftID = signalNode.getAttribute("spacecraftID").toIntOrNull() ?: 0
        return SignalInformation(active, band, dataRate, frequency, power, signalType, spacecraft, spacecraftID)
    }

    fun parseTarget(targetNode: Element): TargetInformation {
        val downlegRange = targetNode.getAttribute("downlegRange").toLongOrNull() ?: 0L
        val id = targetNode.getAttribute("id").toIntOrNull() ?: 0
        val name = targetNode.getAttribute("name").toString()
        val friendlyName = spacecraftNames[name] ?: name
        val rtlt = targetNode.getAttribute("rtlt").toIntOrNull() ?: 0
        val uplegRange = targetNode.getAttribute("uplegRange").toLongOrNull() ?: 0L
        return TargetInformation(downlegRange, friendlyName, id, name, rtlt, uplegRange)
    }

    fun parseDish(dishNode: Element): DishInformation {
        val downSignals = mutableListOf<SignalInformation>()
        val targets = mutableListOf<TargetInformation>()
        val upSignals = mutableListOf<SignalInformation>()
        for(i in 0 until dishNode.childNodes.length) {
            if(dishNode.childNodes.item(i).nodeType == Node.ELEMENT_NODE) {
                val innerNode = dishNode.childNodes.item(i) as Element
                if(innerNode.nodeName == "downSignal") {
                    val signal = parseSignal(innerNode)
                    downSignals.add(signal)
                } else if(innerNode.nodeName == "target") {
                    val target = parseTarget(innerNode)
                    targets.add(target)
                } else if(innerNode.nodeName == "upSignal") {
                    val signal = parseSignal(innerNode)
                    upSignals.add(signal)
                } else {
                    println("Unhandled tag name in dish: ${innerNode.nodeName}")
                }
            }
        }

        val activity = dishNode.getAttribute("activity") as String
        var azimuthAngle = dishNode.getAttribute("azimuthAngle").toIntOrNull() ?: 0
        var elevationAngle = dishNode.getAttribute("elevationAngle").toIntOrNull() ?: 0
        var isArray = dishNode.getAttribute("isArray").toBoolean()
        var isDDOR = dishNode.getAttribute("isDDOR").toBoolean()
        var isMSPA = dishNode.getAttribute("isMSPA").toBoolean()
        var name = dishNode.getAttribute("name") as String
        var windSpeed = dishNode.getAttribute("windSpeed").toIntOrNull() ?: 0
        return DishInformation(activity, azimuthAngle, elevationAngle, isArray,isDDOR, isMSPA, name, windSpeed, downSignals, upSignals, targets)
    }

    fun parseStation(stationNode: Element): StationInformation {
        val friendlyName = stationNode.getAttribute("friendlyName") as String
        val name = stationNode.getAttribute("name") as String
        var timeUTC = stationNode.getAttribute("timeUTC").toLongOrNull()?: 0L
        return StationInformation(emptyList<DishInformation>(), friendlyName, name, timeUTC)
    }

    fun getDataFromDsnAtNasa() : List<StationInformation> {
        val request = HttpRequest.newBuilder().uri(URI.create(DSNURL)).GET().build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())

        var stations = emptyList<StationInformation>()
        var stationInformation: StationInformation? = null
        var dishes = emptyList<DishInformation>()
        response.body().use { inputStream ->
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inputStream)
            val xpath = XPathFactory.newInstance().newXPath()
            val nodes = xpath.evaluate("//dsn", doc, XPathConstants.NODESET) as NodeList
            for (i in 0 until nodes.length) {
                if(nodes.item(i).nodeType == Node.ELEMENT_NODE) {
                    val node = nodes.item(i) as Element
                    if("dsn"==node.nodeName) {
                        for(j in 0 until node.childNodes.length) {
                            if(node.childNodes.item(j).nodeType == Node.ELEMENT_NODE) {
                                val innerNode = node.childNodes.item(j) as Element
                                if(innerNode.nodeName == "station") {
                                    if(stationInformation!=null) {
                                        val tmpStation = stationInformation.copy(dishes = dishes)
                                        stations += tmpStation
                                        dishes = emptyList<DishInformation>()
                                    }
                                    stationInformation = parseStation(innerNode)
                                } else if(innerNode.nodeName == "dish") {
                                    val dishInformation = parseDish(innerNode)
                                    dishes = dishes + dishInformation
                                } else if(innerNode.nodeName == "timestamp") {
                                    // ignore
                                } else {
                                    println("Unhandled tag name in dsp: ${innerNode.nodeName}")
                                }
                            }
                        }
                    } else {
                        println("Unhandled tag name at top level: ${node.nodeName}")
                    }
                }
            }
        }
        if(null!=stationInformation) {
            stationInformation = stationInformation.copy(dishes = dishes)
            stations += stationInformation
        }
        return stations
    }
}

