package com.fortind.zenith.media

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XmpLocationTest {

    private val lightroom = """
        <?xpacket begin="${'﻿'}" id="W5M0MpCehiHzreSzNTczkc9d"?>
        <x:xmpmeta xmlns:x="adobe:ns:meta/">
         <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
          <rdf:Description rdf:about=""
            xmlns:exif="http://ns.adobe.com/exif/1.0/"
            xmlns:photoshop="http://ns.adobe.com/photoshop/1.0/"
            xmlns:hdrgm="http://ns.adobe.com/hdr-gain-map/1.0/"
            exif:GPSLatitude="37,46.494N"
            exif:GPSLongitude="122,25.164W"
            photoshop:City="San Francisco"
            hdrgm:Version="1.0">
           <exif:GPSAltitude>12/1</exif:GPSAltitude>
           <Iptc4xmpExt:LocationCreated xmlns:Iptc4xmpExt="http://iptc.org/std/Iptc4xmpExt/2008-02-29/">
            <rdf:Bag><rdf:li Iptc4xmpExt:City="San Francisco"/></rdf:Bag>
           </Iptc4xmpExt:LocationCreated>
           <dc:description xmlns:dc="http://purl.org/dc/elements/1.1/">
            <rdf:Alt><rdf:li xml:lang="x-default">Café 🌉</rdf:li></rdf:Alt>
           </dc:description>
          </rdf:Description>
         </rdf:RDF>
        </x:xmpmeta>
        <?xpacket end="w"?>
    """.trimIndent()

    @Test
    fun locationFieldsAreFoundInAttributesAndElements() {
        assertTrue(xmpHasLocation(lightroom))
        assertTrue(xmpHasLocation("""<rdf:Description exif:GPSLatitude="1"/>"""))
        assertTrue(xmpHasLocation("""<exif:GPSLongitude>2</exif:GPSLongitude>"""))
        assertTrue(xmpHasLocation("""<rdf:Description drone-dji:GpsLongtitude="+1.0"/>"""))
        assertTrue(xmpHasLocation("""<rdf:Description p:Country="NZ"/>"""))
        assertFalse(xmpHasLocation("""<rdf:Description xmlns:exif="http://ns.adobe.com/exif/1.0/" exif:FNumber="2"/>"""))
        assertFalse(xmpHasLocation("""<rdf:Description p:Citylights="no"/>"""))
    }

    @Test
    fun removingLocationKeepsEverythingElse() {
        val cleaned = withoutXmpLocation(lightroom)

        assertNotNull(cleaned)
        cleaned!!
        assertFalse(xmpHasLocation(cleaned))
        assertFalse(cleaned.contains("37,46"))
        assertFalse(cleaned.contains("San Francisco"))
        assertFalse(cleaned.contains("12/1"))
        assertTrue(cleaned.contains("""hdrgm:Version="1.0""""))
        assertTrue(cleaned.contains("xmlns:exif="))
        assertTrue(cleaned.contains("<dc:description"))
    }

    @Test
    fun theResultIsAsciiWithTextEscapedAndTheByteOrderMarkDropped() {
        val cleaned = withoutXmpLocation(lightroom)!!

        assertTrue(cleaned.all { it.code <= 0x7F })
        assertTrue(cleaned.contains("Caf&#xE9; &#x1F309;"))
        assertTrue(cleaned.contains("""<?xpacket begin="" id="""))
    }

    @Test
    fun attributeValuesWithNonAsciiAreEscapedToo() {
        val cleaned = withoutXmpLocation("""<rdf:Description dc:title="Zürich" exif:GPSLatitude="1"/>""")

        assertEquals("""<rdf:Description dc:title="Z&#xFC;rich"/>""", cleaned)
    }

    @Test
    fun aDocumentTypeDeclarationIsNeverRewritten() {
        assertNull(withoutXmpLocation("""<!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/hosts">]><a exif:GPSLatitude="1"/>"""))
    }

    @Test
    fun anUnclosedLocationElementCannotBeRewritten() {
        assertNull(withoutXmpLocation("""<rdf:Description><exif:GPSLatitude>1</rdf:Description>"""))
    }

    @Test
    fun nonAsciiInsideMarkupCannotBeRewritten() {
        assertNull(withoutXmpLocation("""<rdf:Description p:Ünknown="x" exif:GPSLatitude="1"/>"""))
    }

    @Test
    fun undecodableTextCannotBeRewritten() {
        assertNull(withoutXmpLocation("<rdf:Description exif:GPSLatitude=\"1\" dc:title=\"�\"/>"))
    }

    @Test
    fun aPacketInsideBinaryDataIsScanned() {
        val bytes = ByteArray(5000) { 0x55 } + lightroom.toByteArray() + ByteArray(5000) { 0x0F }

        assertTrue(streamHasXmpLocation(ByteArrayInputStream(bytes)))
    }

    @Test
    fun aPacketSplitAcrossReadsIsStillFound() {
        val bytes = ByteArray(37) { 1 } + lightroom.toByteArray() + ByteArray(37) { 2 }

        for (chunk in listOf(1, 3, 7, 11, 64)) {
            assertTrue("chunk $chunk", streamHasXmpLocation(ByteArrayInputStream(bytes), chunkBytes = chunk))
        }
    }

    @Test
    fun aCleanPacketAndPlainBinaryAreNotFlagged() {
        val clean = withoutXmpLocation(lightroom)!!.toByteArray()

        assertFalse(streamHasXmpLocation(ByteArrayInputStream(ByteArray(3) + clean + ByteArray(3)), chunkBytes = 5))
        assertFalse(streamHasXmpLocation(ByteArrayInputStream(ByteArray(100_000) { (it % 251).toByte() })))
    }

    @Test
    fun aSecondPacketWithLocationIsFoundAfterACleanOne() {
        val clean = withoutXmpLocation(lightroom)!!.toByteArray()

        assertTrue(streamHasXmpLocation(ByteArrayInputStream(clean + ByteArray(10) + lightroom.toByteArray()), chunkBytes = 9))
    }

    @Test
    fun aHugeUnterminatedPacketCountsAsLocation() {
        val bytes = "<x:xmpmeta>".toByteArray() + ByteArray(2_000) { 'a'.code.toByte() }

        assertTrue(streamHasXmpLocation(ByteArrayInputStream(bytes), chunkBytes = 64, maxPacketChars = 1_000))
    }
}
