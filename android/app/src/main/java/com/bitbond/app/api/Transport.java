package com.bitbond.app.api;

import java.io.IOException;
import java.util.Map;

public interface Transport {
    TransportResponse post(String url, Map<String, String> headers, String body) throws IOException;
}
