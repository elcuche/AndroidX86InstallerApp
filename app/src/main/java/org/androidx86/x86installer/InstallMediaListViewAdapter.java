package org.androidx86.x86installer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public class InstallMediaListViewAdapter extends ArrayAdapter<InstallMedia> {

    private final int rowLayout;

    public InstallMediaListViewAdapter(Context context, int resource, int textViewResourceId, List<InstallMedia> objects) {
        super(context, resource, textViewResourceId, objects);
        this.rowLayout = resource;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        InstallMedia item = getItem(position);
        LayoutInflater inflater = (LayoutInflater) getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View rowView = inflater.inflate(rowLayout, parent, false);

        ImageView imageView = (ImageView)rowView.findViewById(R.id.image);
        imageView.setImageResource(item.getImage());

        TextView titleView = (TextView)rowView.findViewById(R.id.title);
        titleView.setText(item.getTitle());

        TextView descriptionView = (TextView)rowView.findViewById(R.id.description);
        descriptionView.setText(item.getDescription());

        rowView.setSelected(item.isSelected());

        return rowView;
    }

}
