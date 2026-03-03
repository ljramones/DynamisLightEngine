package org.dynamislight.sample.save.codecs;

import org.dynamislight.sample.save.TranslationComponent;
import org.dynamissession.api.codec.ComponentCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class TranslationCodec implements ComponentCodec<TranslationComponent> {

    @Override
    public String keyId() {
        return "demo.translation";
    }

    @Override
    public Class<TranslationComponent> type() {
        return TranslationComponent.class;
    }

    @Override
    public byte[] encode(TranslationComponent value) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeFloat(value.x());
            out.writeFloat(value.y());
            out.writeFloat(value.z());
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode TranslationComponent", e);
        }
    }

    @Override
    public TranslationComponent decode(byte[] bytes) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            return new TranslationComponent(in.readFloat(), in.readFloat(), in.readFloat());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to decode TranslationComponent", e);
        }
    }
}
