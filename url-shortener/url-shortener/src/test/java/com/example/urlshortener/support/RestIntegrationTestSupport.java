package com.example.urlshortener.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Shared base for *IT tests that also need to call the app over real HTTP on a random port. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class RestIntegrationTestSupport extends IntegrationTestSupport {

  @LocalServerPort private int port;

  @Autowired protected TestRestTemplate restTemplate;

  protected String url(String path) {
    return "http://localhost:" + port + path;
  }
}
