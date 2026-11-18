package com.shadowbase.backend.heap.parser;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.shadowbase.backend.heap.model.GCRootType;
import com.shadowbase.backend.heap.model.HeapObject;
import com.shadowbase.backend.heap.model.HeapSnapshot;

class HprofParserTest {

    @Test
    void testParseValidBinaryHprof() throws Exception {
        byte[] hprofBytes = createSyntheticHprof();

        HprofParser parser = new HprofParser();
        HeapSnapshot snapshot = parser.parse(hprofBytes);

        assertNotNull(snapshot);
        assertEquals("JAVA PROFILE 1.0.2", snapshot.getHeaderVersion());
        assertEquals(8, snapshot.getIdSize());

        // Objects 50 (Class), 100 (Instance), 200 (Instance), 300 (Primitive array)
        assertTrue(snapshot.getObjects().size() >= 3);
        assertNotNull(snapshot.getObject(100L));
        assertNotNull(snapshot.getObject(200L));
        assertNotNull(snapshot.getObject(300L));

        HeapObject obj100 = snapshot.getObject(100L);
        assertEquals("com.example.User", obj100.getClassName());
        assertTrue(obj100.isGcRoot());
        assertEquals(GCRootType.JAVA_FRAME, obj100.getRootType());
        assertTrue(obj100.getOutReferences().contains(200L));

        HeapObject arrayObj = snapshot.getObject(300L);
        assertTrue(arrayObj.isArray());
        assertTrue(arrayObj.isPrimitiveArray());
        assertEquals(10, arrayObj.getArrayLength());

        assertEquals(1, snapshot.getGcRoots().size());
        assertEquals(100L, snapshot.getGcRoots().get(0).getObjectId());
    }

    @Test
    void testParseInvalidHeader_ThrowsException() {
        byte[] invalidHeader = "INVALID HEADER\0\0\0\0\0".getBytes(StandardCharsets.UTF_8);
        HprofParser parser = new HprofParser();
        assertThrows(HprofParseException.class, () -> parser.parse(invalidHeader));
    }

    private byte[] createSyntheticHprof() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);

        // Header: null-terminated "JAVA PROFILE 1.0.2"
        out.writeBytes("JAVA PROFILE 1.0.2");
        out.writeByte(0); // null terminator

        // idSize = 8
        out.writeInt(8);

        // timestamp
        out.writeLong(System.currentTimeMillis());

        // Record 1: STRING for class name "com.example.User"
        byte[] classNameBytes = "com/example/User".getBytes(StandardCharsets.UTF_8);
        out.writeByte(0x01); // TAG_STRING
        out.writeInt(0);     // time
        out.writeInt(8 + classNameBytes.length); // length
        out.writeLong(10L);  // stringId
        out.write(classNameBytes);

        // Record 2: STRING for field name "friend"
        byte[] fieldNameBytes = "friend".getBytes(StandardCharsets.UTF_8);
        out.writeByte(0x01);
        out.writeInt(0);
        out.writeInt(8 + fieldNameBytes.length);
        out.writeLong(11L);
        out.write(fieldNameBytes);

        // Record 3: LOAD_CLASS
        out.writeByte(0x02); // TAG_LOAD_CLASS
        out.writeInt(0);
        out.writeInt(4 + 8 + 4 + 8); // length = 24
        out.writeInt(1);     // class serial
        out.writeLong(50L);  // classObjectId
        out.writeInt(0);     // stackTraceSerial
        out.writeLong(10L);  // classNameStringId ("com/example/User")

        // Record 4: HEAP_DUMP_SEGMENT
        ByteArrayOutputStream segmentBaos = new ByteArrayOutputStream();
        DataOutputStream segmentOut = new DataOutputStream(segmentBaos);

        // Sub 1: ROOT_JAVA_FRAME for object 100
        segmentOut.writeByte(0x03); // SUB_ROOT_JAVA_FRAME
        segmentOut.writeLong(100L); // objectId
        segmentOut.writeInt(1);     // threadSerial
        segmentOut.writeInt(0);     // frameNumber

        // Sub 2: CLASS_DUMP for class 50
        segmentOut.writeByte(0x20); // SUB_CLASS_DUMP
        segmentOut.writeLong(50L);  // classObjectId
        segmentOut.writeInt(0);     // stackTrace
        segmentOut.writeLong(0L);   // superclass
        segmentOut.writeLong(0L);   // classLoader
        segmentOut.writeLong(0L);   // signers
        segmentOut.writeLong(0L);   // protDomain
        segmentOut.writeLong(0L);   // reserved1
        segmentOut.writeLong(0L);   // reserved2
        segmentOut.writeInt(24);    // instanceSize
        segmentOut.writeShort(0);   // constantPoolCount
        segmentOut.writeShort(0);   // staticFieldCount
        segmentOut.writeShort(1);   // instanceFieldCount
        segmentOut.writeLong(11L);  // fieldNameId ("friend")
        segmentOut.writeByte(2);    // type = OBJECT (2)

        // Sub 3: INSTANCE_DUMP for object 100 pointing to 200
        segmentOut.writeByte(0x21); // SUB_INSTANCE_DUMP
        segmentOut.writeLong(100L); // objectId
        segmentOut.writeInt(0);     // stackTrace
        segmentOut.writeLong(50L);  // classObjectId
        segmentOut.writeInt(8);     // numBytes
        segmentOut.writeLong(200L); // field value: reference to object 200

        // Sub 4: INSTANCE_DUMP for object 200 pointing to null (0)
        segmentOut.writeByte(0x21);
        segmentOut.writeLong(200L);
        segmentOut.writeInt(0);
        segmentOut.writeLong(50L);
        segmentOut.writeInt(8);
        segmentOut.writeLong(0L);

        // Sub 5: PRIMITIVE_ARRAY_DUMP for object 300 (byte array of 10 elements)
        segmentOut.writeByte(0x23);
        segmentOut.writeLong(300L);
        segmentOut.writeInt(0);
        segmentOut.writeInt(10);    // numElements
        segmentOut.writeByte(8);     // type = byte (8)
        segmentOut.write(new byte[10]); // 10 bytes

        byte[] segmentBytes = segmentBaos.toByteArray();

        // Write HEAP_DUMP_SEGMENT record
        out.writeByte(0x1C); // TAG_HEAP_DUMP_SEGMENT
        out.writeInt(0);
        out.writeInt(segmentBytes.length);
        out.write(segmentBytes);

        // TAG_HEAP_DUMP_END
        out.writeByte(0x2C);
        out.writeInt(0);
        out.writeInt(0);

        out.flush();
        return baos.toByteArray();
    }
}
