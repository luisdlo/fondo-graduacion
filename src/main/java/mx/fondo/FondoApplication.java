package mx.fondo;

import org.h2.tools.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.sql.SQLException;

@SpringBootApplication
public class FondoApplication {

    public static void main(String[] args) {
        SpringApplication.run(FondoApplication.class, args);
    }

    /**
     * Servidor TCP de H2 para conectarse en vivo desde IntelliJ (por túnel SSH).
     * Solo arranca si está definido fondo.h2.tcp-port (en el VPS: FONDO_H2_TCP_PORT en docker-compose).
     * URL: jdbc:h2:tcp://localhost:9092//opt/fondo/data/fondo
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnProperty("fondo.h2.tcp-port")
    public Server h2TcpServer(@Value("${fondo.h2.tcp-port}") String puerto) throws SQLException {
        // -tcpAllowOthers: la conexión llega desde la red de Docker. -ifExists: no deja crear BDs nuevas.
        return Server.createTcpServer("-tcp", "-tcpAllowOthers", "-tcpPort", puerto, "-ifExists");
    }
}
