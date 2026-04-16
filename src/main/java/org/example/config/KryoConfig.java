package org.example.config;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.example.dto.SessionMessage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Kryo 序列化配置
 */
@Configuration
public class KryoConfig {

    @Bean
    public Kryo kryo() {
        Kryo kryo = new Kryo();
        kryo.register(SessionMessage.class, 1);
        kryo.register(byte[].class, 2);
        kryo.setRegistrationRequired(true);
        kryo.setReferences(false);
        return kryo;
    }

    @Bean
    public KryoFactory kryoFactory(Kryo kryo) {
        return new KryoFactory(kryo);
    }

    public static class KryoFactory {
        private final Kryo kryo;

        public KryoFactory(Kryo kryo) {
            this.kryo = kryo;
        }

        public byte[] serialize(Object obj) {
            Output output = new Output(256, -1);
            kryo.writeClassAndObject(output, obj);
            return output.getBuffer();
        }

        @SuppressWarnings("unchecked")
        public <T> T deserialize(byte[] bytes) {
            Input input = new Input(bytes);
            return (T) kryo.readClassAndObject(input);
        }

        public String serializeToString(Object obj) {
            return java.util.Base64.getEncoder().encodeToString(serialize(obj));
        }

        public <T> T deserializeFromString(String data) {
            return deserialize(java.util.Base64.getDecoder().decode(data));
        }
    }
}