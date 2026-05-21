package com.example.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Tool: get_current_weather
 * Returns current weather conditions for a given city using the Open-Meteo API
 * (https://open-meteo.com) — no API key required.
 *
 * Flow:
 *   1. Geocode the city name via the Open-Meteo Geocoding API to get lat/lon.
 *   2. Fetch current weather from the Open-Meteo Forecast API using those coordinates.
 *   3. Return a human-readable summary.
 */
public class WeatherTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(WeatherTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String GEOCODING_URL =
            "https://geocoding-api.open-meteo.com/v1/search?name=%s&count=1&language=en&format=json";
    private static final String WEATHER_URL =
            "https://api.open-meteo.com/v1/forecast"
            + "?latitude=%s&longitude=%s"
            + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,"
            + "weather_code,wind_speed_10m,wind_direction_10m"
            + "&temperature_unit=celsius&wind_speed_unit=kmh&timezone=auto";

    private final HttpClient httpClient;

    public WeatherTool() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** Package-private constructor for testing with a mock/custom HttpClient. */
    WeatherTool(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String getName() {
        return "get_current_weather";
    }

    @Override
    public String getDescription() {
        return "Returns the current weather conditions (temperature, humidity, wind, description) "
                + "for a given city. Uses the Open-Meteo API — no API key required.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode city = properties.putObject("city");
        city.put("type", "string");
        city.put("description", "The city name to get weather for, e.g. 'Paris', 'New York', 'Tokyo'.");

        schema.putArray("required").add("city");
        return schema;
    }

    @Override
    public String execute(ObjectNode input) {
        if (!input.has("city") || input.get("city").asText().isBlank()) {
            return "Error: 'city' parameter is required.";
        }

        String city = input.get("city").asText().trim();
        log.debug("Fetching weather for city: {}", city);

        try {
            // Step 1: Geocode the city
            double[] coords = geocode(city);
            if (coords == null) {
                return "Error: Could not find location for city '" + city + "'. "
                        + "Please check the spelling or try a nearby major city.";
            }
            double lat = coords[0];
            double lon = coords[1];
            // Step 2: Fetch weather
            return fetchWeather(city, lat, lon);

        } catch (Exception e) {
            log.error("Weather tool failed for city '{}': {}", city, e.getMessage(), e);
            return "Error fetching weather for '" + city + "': " + e.getMessage();
        }
    }

    /**
     * Geocodes a city name to [latitude, longitude].
     * Returns null if the city is not found.
     */
    private double[] geocode(String city) throws Exception {
        String encoded = URLEncoder.encode(city, StandardCharsets.UTF_8);
        String url = String.format(GEOCODING_URL, encoded);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Geocoding API returned HTTP " + response.statusCode());
        }

        JsonNode root = MAPPER.readTree(response.body());
        JsonNode results = root.path("results");

        if (!results.isArray() || results.isEmpty()) {
            return null;
        }

        JsonNode first = results.get(0);
        double lat = first.path("latitude").asDouble();
        double lon = first.path("longitude").asDouble();
        return new double[]{lat, lon};
    }

    /**
     * Fetches current weather for the given coordinates and returns a formatted string.
     */
    private String fetchWeather(String city, double lat, double lon) throws Exception {
        String url = String.format(WEATHER_URL, lat, lon);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Weather API returned HTTP " + response.statusCode());
        }

        JsonNode root = MAPPER.readTree(response.body());
        JsonNode current = root.path("current");
        JsonNode units = root.path("current_units");

        double temp        = current.path("temperature_2m").asDouble();
        double feelsLike   = current.path("apparent_temperature").asDouble();
        int    humidity    = current.path("relative_humidity_2m").asInt();
        double windSpeed   = current.path("wind_speed_10m").asDouble();
        int    windDir     = current.path("wind_direction_10m").asInt();
        int    weatherCode = current.path("weather_code").asInt();

        String tempUnit  = units.path("temperature_2m").asText("°C");
        String windUnit  = units.path("wind_speed_10m").asText("km/h");

        String description = describeWeatherCode(weatherCode);
        String windDirStr  = degreesToCompass(windDir);

        return String.format(
                "Current weather in %s:\n"
                + "  Conditions:    %s\n"
                + "  Temperature:   %.1f%s (feels like %.1f%s)\n"
                + "  Humidity:      %d%%\n"
                + "  Wind:          %.1f %s from the %s",
                city, description,
                temp, tempUnit, feelsLike, tempUnit,
                humidity,
                windSpeed, windUnit, windDirStr
        );
    }

    /** Maps WMO weather interpretation codes to human-readable descriptions. */
    private String describeWeatherCode(int code) {
        return switch (code) {
            case 0          -> "Clear sky";
            case 1          -> "Mainly clear";
            case 2          -> "Partly cloudy";
            case 3          -> "Overcast";
            case 45, 48     -> "Foggy";
            case 51, 53, 55 -> "Drizzle";
            case 61, 63, 65 -> "Rain";
            case 71, 73, 75 -> "Snow";
            case 77         -> "Snow grains";
            case 80, 81, 82 -> "Rain showers";
            case 85, 86     -> "Snow showers";
            case 95         -> "Thunderstorm";
            case 96, 99     -> "Thunderstorm with hail";
            default         -> "Unknown (code " + code + ")";
        };
    }

    /** Converts a wind direction in degrees to a compass point string. */
    private String degreesToCompass(int degrees) {
        String[] points = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int index = (int) Math.round(degrees / 45.0) % 8;
        return points[index];
    }
}
