package com.hdfs.transfer.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
@EnableScheduling
public class ServerApplication implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(ServerApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("");
        log.info("  _|    _|            _|                          _|_|                _|                      _|");
        log.info("  _|    _|    _|_|_|  _|  _|      _|_|_|    _|_|    _|  _|_|      _|_|_|_|  _|    _|        _|");
        log.info("  _|_|_|_|  _|    _|  _|_|      _|_|_|_|  _|_|_|_|_|  _|_|_|_|_|  _|    _|    _|_|_|        _|");
        log.info("      _|    _|    _|  _|  _|    _|        _|        _|  _|            _|_|_|  _|    _|        _|");
        log.info("      _|      _|_|_|  _|    _|    _|_|_|    _|_|_|_|  _|                _|  _|    _|        _|");
        log.info("");
        log.info("  hdfs-file-transfer-server 服务启动成功!");
        log.info("");
    }
}
