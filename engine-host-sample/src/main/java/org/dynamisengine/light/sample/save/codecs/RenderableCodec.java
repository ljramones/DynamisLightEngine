package org.dynamisengine.light.sample.save.codecs;

import org.dynamisengine.light.sample.save.RenderableComponent;
import org.dynamisengine.session.api.codec.ComponentCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class RenderableCodec implements ComponentCodec<RenderableComponent> {

    @Override
    public String keyId() {
        return "demo.renderable";
    }

    @Override
    public Class<RenderableComponent> type() {
        return RenderableComponent.class;
    }

    @Override
    public byte[] encode(RenderableComponent value) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(value.meshHandle());
            out.writeUTF(value.materialKey());
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode RenderableComponent", e);
        }
    }

    @Override
    public RenderableComponent decode(byte[] bytes) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            return new RenderableComponent(in.readInt(), in.readUTF());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to decode RenderableComponent", e);
        }
    }
}
