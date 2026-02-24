package com.example.myapplication;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.expressions.Expression;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.FillLayer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.sources.GeoJsonSource;

import static org.maplibre.android.style.layers.PropertyFactory.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OverpassMapHelper {

    private static final String TAG = "OverpassMap";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * Fetches Overpass data for the given coordinates and renders it on the map.
     */
    public static void loadOverpassData(MapLibreMap map, Style style,
                                        double lat, double lng, int radius) {
        executor.execute(() -> {
            try {
                String url = buildOverpassUrl(lat, lng, radius);
                Log.d(TAG, "Requesting Overpass API: " + url);

                String json = fetchData(url);
                Log.d(TAG, "Received response, length: " + json.length());

                String geoJson = overpassToGeoJson(json);

                new Handler(Looper.getMainLooper()).post(() -> {
                    try {
                        addDataToMap(style, geoJson, lat, lng);
                        Log.d(TAG, "Map data loaded successfully");
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to add data to map", e);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to load Overpass data", e);
            }
        });
    }

    static String buildOverpassUrl(double lat, double lng, int radius) {
        String query = "[out:json][timeout:25];" +
                "(way[\"highway\"](around:" + radius + "," + lat + "," + lng + ");" +
                " nwr[\"building\"](around:" + radius + "," + lat + "," + lng + ");" +
                " nwr[\"amenity\"](around:" + radius + "," + lat + "," + lng + ");" +
                ");out geom;";

        try {
            String encoded = URLEncoder.encode(query, "UTF-8");
            return "https://overpass-api.de/api/interpreter?data=" + encoded;
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private static String fetchData(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", "SmartDig/1.0");

        try {
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new IOException("Overpass API returned HTTP " + code);
            }

            InputStream is = conn.getInputStream();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    static String overpassToGeoJson(String overpassJson) throws JSONException {
        JSONObject overpass = new JSONObject(overpassJson);
        JSONArray elements = overpass.getJSONArray("elements");
        JSONArray features = new JSONArray();

        for (int i = 0; i < elements.length(); i++) {
            JSONObject element = elements.getJSONObject(i);
            String type = element.getString("type");

            JSONObject properties = new JSONObject();
            if (element.has("tags")) {
                JSONObject tags = element.getJSONObject("tags");
                Iterator<String> keys = tags.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    properties.put(key, tags.get(key));
                }
            }

            JSONObject geometry = new JSONObject();

            if ("node".equals(type) && element.has("lat") && element.has("lon")) {
                geometry.put("type", "Point");
                JSONArray coords = new JSONArray();
                coords.put(element.getDouble("lon"));
                coords.put(element.getDouble("lat"));
                geometry.put("coordinates", coords);

            } else if ("way".equals(type) && element.has("geometry")) {
                JSONArray geomArray = element.getJSONArray("geometry");
                JSONArray coords = new JSONArray();
                for (int j = 0; j < geomArray.length(); j++) {
                    JSONObject pt = geomArray.getJSONObject(j);
                    JSONArray coord = new JSONArray();
                    coord.put(pt.getDouble("lon"));
                    coord.put(pt.getDouble("lat"));
                    coords.put(coord);
                }

                boolean isArea = properties.has("building")
                        || (properties.has("amenity") && !properties.has("highway"));

                if (isArea && coords.length() >= 3) {
                    JSONArray first = coords.getJSONArray(0);
                    JSONArray last = coords.getJSONArray(coords.length() - 1);
                    if (first.getDouble(0) != last.getDouble(0)
                            || first.getDouble(1) != last.getDouble(1)) {
                        JSONArray closing = new JSONArray();
                        closing.put(first.getDouble(0));
                        closing.put(first.getDouble(1));
                        coords.put(closing);
                    }
                    geometry.put("type", "Polygon");
                    JSONArray ring = new JSONArray();
                    ring.put(coords);
                    geometry.put("coordinates", ring);
                } else {
                    geometry.put("type", "LineString");
                    geometry.put("coordinates", coords);
                }

            } else {
                continue;
            }

            JSONObject feature = new JSONObject();
            feature.put("type", "Feature");
            feature.put("properties", properties);
            feature.put("geometry", geometry);
            features.put(feature);
        }

        JSONObject collection = new JSONObject();
        collection.put("type", "FeatureCollection");
        collection.put("features", features);
        return collection.toString();
    }

    private static void addDataToMap(Style style, String geoJson,
                                     double lat, double lng) throws JSONException {
        GeoJsonSource source = new GeoJsonSource("overpass-source", geoJson);
        style.addSource(source);

        // Buildings — semi-transparent blue fill
        FillLayer buildings = new FillLayer("buildings-layer", "overpass-source");
        buildings.setFilter(Expression.has("building"));
        buildings.setProperties(
                fillColor(Color.parseColor("#3A7BD5")),
                fillOpacity(0.45f),
                fillOutlineColor(Color.parseColor("#5A9BD5"))
        );
        style.addLayer(buildings);

        // Roads — golden lines
        LineLayer roads = new LineLayer("roads-layer", "overpass-source");
        roads.setFilter(Expression.has("highway"));
        roads.setProperties(
                lineColor(Color.parseColor("#FFD700")),
                lineWidth(1.5f),
                lineOpacity(0.8f)
        );
        style.addLayer(roads);

        // Amenity areas (non-building polygons like parking, parks)
        FillLayer amenityAreas = new FillLayer("amenity-areas-layer", "overpass-source");
        amenityAreas.setFilter(Expression.all(
                Expression.has("amenity"),
                Expression.not(Expression.has("building"))
        ));
        amenityAreas.setProperties(
                fillColor(Color.parseColor("#FF6B6B")),
                fillOpacity(0.3f),
                fillOutlineColor(Color.parseColor("#FF9999"))
        );
        style.addLayer(amenityAreas);

        // Amenity points — red circles
        CircleLayer amenityPoints = new CircleLayer("amenity-points-layer", "overpass-source");
        amenityPoints.setFilter(Expression.has("amenity"));
        amenityPoints.setProperties(
                circleColor(Color.parseColor("#FF6B6B")),
                circleRadius(4f),
                circleOpacity(0.8f),
                circleStrokeColor(Color.parseColor("#FF9999")),
                circleStrokeWidth(1f)
        );
        style.addLayer(amenityPoints);

        // Current position marker — bright green dot
        JSONObject posFeature = new JSONObject();
        posFeature.put("type", "Feature");
        JSONObject posGeom = new JSONObject();
        posGeom.put("type", "Point");
        posGeom.put("coordinates", new JSONArray().put(lng).put(lat));
        posFeature.put("geometry", posGeom);
        posFeature.put("properties", new JSONObject());

        JSONObject posCollection = new JSONObject();
        posCollection.put("type", "FeatureCollection");
        posCollection.put("features", new JSONArray().put(posFeature));

        GeoJsonSource posSource = new GeoJsonSource("position-source", posCollection.toString());
        style.addSource(posSource);

        CircleLayer posLayer = new CircleLayer("position-layer", "position-source");
        posLayer.setProperties(
                circleColor(Color.parseColor("#00FF00")),
                circleRadius(6f),
                circleStrokeColor(Color.WHITE),
                circleStrokeWidth(2f)
        );
        style.addLayer(posLayer);
    }
}
