package com.example.routeidentifier;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;

import org.bson.Document;
import org.opencv.android.OpenCVLoader;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;

import com.mongodb.client.model.Filters;
import com.mongodb.stitch.android.core.Stitch;
import com.mongodb.stitch.android.core.StitchAppClient;
import com.mongodb.stitch.android.core.auth.StitchUser;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteFindIterable;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoClient;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection;
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential;

public class StartActivity extends AppCompatActivity {
    private static final String TAG = "StartActivity";
    //public static JSONMap jsonMap;
    public static StitchAppClient client;
    public static RemoteMongoClient mongoClient;
    public static RemoteMongoCollection<Document> coll;
    public static RemoteMongoCollection<Document> image_coll;
    public static StitchUser stitchUser;
    ArrayList<Document> suggestions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_activity);

        suggestions = new ArrayList<>();

        // Setup MongoDB
        if (client == null) {
            client = Stitch.initializeDefaultAppClient(getResources().getString(R.string.my_app_id));
            mongoClient = client.getServiceClient(RemoteMongoClient.factory, "mongodb-atlas");
            coll = mongoClient.getDatabase("iw-08").getCollection("route-data");
            image_coll = mongoClient.getDatabase("iw-08").getCollection("image-data");

            // Authenticate with MongoDB
            client.getAuth().loginWithCredential(new AnonymousCredential())
                    .addOnSuccessListener(stitchUser -> {
                        Log.e(TAG, "Authenticated");
                        this.stitchUser = stitchUser;
                        setupApp();
                    });
        }
        else {
            setupApp();
        }

    }

    private void setupApp() {
        // Get the views for the search bar and list to display search results
        final ListView listView = findViewById(R.id.search_list);
        SearchView searchView = findViewById(R.id.search_bar);

        // Handle queries from the search bar
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                // Clear current suggestions
                suggestions.clear();

                // Return if empty text
                if (newText.equals("")) return true;

                // Search database collection for search term
                Pattern pattern = Pattern.compile("(^| )" + newText, Pattern.CASE_INSENSITIVE);
                RemoteFindIterable<Document> query = coll.find(Filters.regex("_id", pattern));
                ArrayList<Document> result = new ArrayList<>();
                query.into(result).addOnSuccessListener(documents -> {
                    // Display results
                    suggestions.addAll(documents);
                    ArrayAdapter<Document> adapter = new MySimpleArrayAdapter(StartActivity.this, suggestions);
                    listView.setAdapter(adapter);
                });

                return true;
            }
        });

        // Handle clicking on search result
        listView.setOnItemClickListener((parent, view, position, id) -> {
            // Get the search result that was clicked
            Document selected = suggestions.get(position);

            // Start a new page to display information about the search result
            Intent intent = new Intent(StartActivity.this, DisplayClimbingArea.class);
            DisplayClimbingArea.setDocument(selected);
            startActivity(intent);
        });

    }

    // Start activity to measure distances between bolts
    public void onMeasureBoltsClick(View view) {
        Intent intent = new Intent(this, MeasureBoltDistanceActivity.class);
        startActivity(intent);
    }

    // Start activity to identify the location of a route using camera
    public void onIdentifyClick(View view) {
        Intent intent = new Intent(this, CameraActivity.class);
        startActivity(intent);
    }

    // Start activity to let users add images to database
    public void onCreateTopoClick(View view) {
        Intent intent = new Intent(this, DrawTopoActivity.class);
        startActivity(intent);
    }

    // Class to dynamically update list view for search items
    public static class MySimpleArrayAdapter extends ArrayAdapter<Document> {
        private final Context context;
        private final ArrayList<Document> searchData;

        public MySimpleArrayAdapter(Context context, ArrayList<Document> values) {
            super(context, -1, values);
            this.context = context;
            this.searchData = values;
            notifyDataSetChanged();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // Get the view for the search item (from list_search.xml)
            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) context
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.list_search, parent, false);
            }
            TextView txtName = convertView.findViewById(R.id.suggestion);
            TextView txtExtra = convertView.findViewById(R.id.suggestionExtra);
            TextView txtType = convertView.findViewById(R.id.suggestionType);

            // Update the view with the search data
            Document search = searchData.get(position);
            txtName.setText(search.getString("_id"));
            txtExtra.setText(String.format(Locale.US, "%d Areas", search.getInteger("n")));
            txtType.setText("Area");

            return convertView;
        }
    }

    // Example of how to use google maps
    public void onNavigate(View view) {
        Uri gmmIntentUri = Uri.parse("geo:37.7749,-122.4194");
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        if (mapIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(mapIntent);
        }
    }
}
