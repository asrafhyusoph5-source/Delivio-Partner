package com.example

import com.example.data.model.AddressLocation
import com.example.util.OsmRouteHelper
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AddressFormattingUnitTest {

    @Test
    fun buildGeocoderAddress_combinesFourPartsInExactOrder() {
        val formatted = OsmRouteHelper.buildGeocoderAddress(
            road = "Rizal Avenue",
            houseNumber = "123",
            barangay = "San Pedro",
            suburb = "Suburb Ignored",
            neighbourhood = "Neighbourhood Ignored",
            city = "Puerto Princesa",
            town = "Town Ignored",
            municipality = "Municipality Ignored",
            province = "Palawan",
            displayName = "123, Rizal Avenue, San Pedro, Puerto Princesa, Palawan, 5300, Philippines"
        )
        assertEquals("123 Rizal Avenue, San Pedro, Puerto Princesa, Palawan", formatted)
    }

    @Test
    fun buildGeocoderAddress_respectsLocalityAndCityFallbacks() {
        val withSuburbAndTown = OsmRouteHelper.buildGeocoderAddress(
            road = "National Highway",
            suburb = "Poblacion",
            neighbourhood = "Zone 1",
            town = "El Nido",
            municipality = "El Nido Municipality",
            province = "Palawan"
        )
        assertEquals("National Highway, Poblacion, El Nido, Palawan", withSuburbAndTown)

        val withNeighbourhoodAndMunicipality = OsmRouteHelper.buildGeocoderAddress(
            road = "Mabini Street",
            neighbourhood = "Barangay 2",
            municipality = "Coron",
            province = "Palawan"
        )
        assertEquals("Mabini Street, Barangay 2, Coron, Palawan", withNeighbourhoodAndMunicipality)
    }

    @Test
    fun buildGeocoderAddress_removesEmptyAndRepeatedPartsIgnoringCapitals() {
        val formatted = OsmRouteHelper.buildGeocoderAddress(
            road = "   ",
            houseNumber = "",
            barangay = "  Taguig ",
            city = "taguig",
            province = "  "
        )
        assertEquals("Taguig", formatted)

        val repeatedAcrossThree = OsmRouteHelper.buildGeocoderAddress(
            road = "Manila Road",
            suburb = "MANILA ROAD",
            city = "Manila",
            province = "manila"
        )
        assertEquals("Manila Road, Manila", repeatedAcrossThree)
    }

    @Test
    fun buildGeocoderAddress_fallsBackToTrimmedDisplayNameOnlyWhenNothingElseExists() {
        val fallback = OsmRouteHelper.buildGeocoderAddress(
            road = " ",
            houseNumber = null,
            barangay = "",
            suburb = null,
            neighbourhood = "  ",
            city = null,
            town = "",
            municipality = null,
            province = "",
            displayName = "  Puerto Princesa Baywalk, Palawan, Philippines  "
        )
        assertEquals("Puerto Princesa Baywalk, Palawan, Philippines", fallback)

        // When at least one part exists, displayName is NOT used
        val partial = OsmRouteHelper.buildGeocoderAddress(
            province = "Palawan",
            displayName = "Full Unformatted Display Name"
        )
        assertEquals("Palawan", partial)
    }

    @Test
    fun formatGeocoderAddress_parsesJsonAndHandlesJsonNull() {
        val json = JSONObject(
            """
            {
              "house_number": "88",
              "road": "Bonifacio High Street",
              "barangay": null,
              "suburb": "Fort Bonifacio",
              "city": "Taguig",
              "province": "Metro Manila"
            }
            """.trimIndent()
        )
        assertEquals(
            "88 Bonifacio High Street, Fort Bonifacio, Taguig, Metro Manila",
            OsmRouteHelper.formatGeocoderAddress(json, "Fallback Display Name")
        )

        val emptyAddrJson = JSONObject("""{"country": "Philippines"}""")
        assertEquals(
            "Trimmed Fallback",
            OsmRouteHelper.formatGeocoderAddress(emptyAddrJson, "  Trimmed Fallback  ")
        )
    }

    @Test
    fun addressLocationFormatted_doesNotDuplicateCity() {
        val alreadyFull = AddressLocation(
            street = "123 Rizal Ave, San Pedro, Puerto Princesa, Palawan",
            city = "puerto princesa"
        )
        assertEquals("123 Rizal Ave, San Pedro, Puerto Princesa, Palawan", alreadyFull.formatted)

        val separateStreetAndCity = AddressLocation(
            street = "123 Rizal Ave",
            city = "Taguig"
        )
        assertEquals("123 Rizal Ave, Taguig", separateStreetAndCity.formatted)
    }
}
