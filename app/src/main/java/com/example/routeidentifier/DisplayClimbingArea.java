package com.example.routeidentifier;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.TextView;

import com.mongodb.client.model.Filters;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteFindIterable;

import org.bson.Document;

import java.util.ArrayList;
import java.util.Locale;
import java.util.TreeMap;
import static com.example.routeidentifier.StartActivity.coll;

public class DisplayClimbingArea extends AppCompatActivity {
    private static Document document;
    private static String path;
    private static String regionName;

    private TextView headerName;
    private TextView headerNumAreas;
    private ImageView backgroundImage;
    private ExpandableListView listView;

    // Save information about the clicked search result
    public static void setDocument(Document parent) {
        document = parent;
        regionName = parent.getString("_id");
        path = parent.getString("path") + (regionName + ",");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_region);

        // Get the views to display the information about the search result
        headerName = findViewById(R.id.areaViewName);
        headerNumAreas = findViewById(R.id.areaViewNumAreas);
        backgroundImage = findViewById(R.id.areaViewBackgroundImage);
        listView = findViewById(R.id.areaViewList);
        setupActivity();
    }

    public void setupActivity() {
        // Setup basic layout
        headerName.setText(regionName);
        headerNumAreas.setText(String.format(Locale.US, "%d Areas", document.getInteger("n")));
        backgroundImage.setImageResource(R.drawable.climbing1);

        // Search database for the children of clicked search result
        RemoteFindIterable<Document> query = coll.find(Filters.regex("path", path + "$"));
        ArrayList<Document> result = new ArrayList<>();

        // Display children data in expandable list
        query.into(result).addOnSuccessListener(documents -> {
            MyExpandableListAdapter myListAdapter = new MyExpandableListAdapter(this, documents);
            listView.setAdapter(myListAdapter);
            listView.setOnChildClickListener((parent, v, groupPosition, childPosition, id) -> false);
        });
    }



    public static class MyExpandableListAdapter extends BaseExpandableListAdapter {
        private Context context;
        private ArrayList<Document> listDataHeader; // parent names
        // map from parent name to children data
        private TreeMap<String, ArrayList<Document>> listDataChild;

        public MyExpandableListAdapter(Context context, ArrayList<Document> listDataHeader) {
            this.context = context;
            this.listDataHeader = listDataHeader;
            this.listDataChild = new TreeMap<>();

            // Iterate through parent data
            for (Document d : listDataHeader) {
                // Search for children of parent in database
                String childPath = d.getString("path") + d.getString("_id") + ",$";
                RemoteFindIterable<Document> query = coll.find(Filters.regex("path", childPath));
                ArrayList<Document> result = new ArrayList<>();

                query.into(result).addOnSuccessListener(documents -> {
                    listDataChild.put(d.getString("_id"), documents);
                }).addOnFailureListener(e -> {
                    listDataChild.put(d.getString("_id"), result);
                });
            }
        }

        @Override
        public Object getChild(int groupPosition, int childPosition) {
            String key = this.listDataHeader.get(groupPosition).getString("_id");
            ArrayList<Document> children = listDataChild.get(key);
            if (children.size() == 0) return null;
            return children.get(childPosition);
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        @Override
        public View getChildView(int groupPosition, final int childPosition,
                                 boolean isLastChild, View convertView, ViewGroup parent) {

            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) this.context
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.list_child, null);
            }

            // Get the child data
            final Document child = (Document) getChild(groupPosition, childPosition);

            // Update view with the data
            TextView txtNameChild = convertView.findViewById(R.id.areaName);
            txtNameChild.setText(child.getString("_id"));
            TextView txtAreasChild = convertView.findViewById(R.id.numRoutes);
            txtAreasChild.setText(String.format(Locale.US, "%d Areas", child.getInteger("n")));

            return convertView;
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            return this.listDataHeader.get(groupPosition).getInteger("n");
        }

        @Override
        public Object getGroup(int groupPosition) {
            return this.listDataHeader.get(groupPosition);
        }

        @Override
        public int getGroupCount() {
            return this.listDataHeader.size();
        }

        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded,
                                 View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) this.context
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.list_parent, null);
            }
            // Get parent data
            Document d = (Document) getGroup(groupPosition);

            // Update view with name
            TextView lblListHeader = convertView.findViewById(R.id.stateName);
            lblListHeader.setTypeface(null, Typeface.BOLD);
            lblListHeader.setText(d.getString("_id"));

            // Update view with number of children
            int numAreas = getChildrenCount(groupPosition);
            TextView lblNumAreas = convertView.findViewById(R.id.numAreas);
            lblNumAreas.setText(String.format(Locale.US, "%d Areas", numAreas));

            return convertView;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            Document d = (Document) getChild(groupPosition, childPosition);
            return !d.containsKey("grade");
        }
    }
}
