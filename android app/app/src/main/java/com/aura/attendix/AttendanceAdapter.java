package com.aura.attendix;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class AttendanceAdapter extends RecyclerView.Adapter<AttendanceAdapter.ViewHolder> {

    private JSONArray data;

    public AttendanceAdapter(JSONArray data) {
        this.data = data;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_attendance_subject, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        try {
            JSONObject subjectData = data.getJSONObject(position);

            // 1. Fetch Data using Backend Keys
            String name = subjectData.optString("subject_name", "Unknown Subject");
            String code = subjectData.optString("subject_code", "---");
            String teacher = subjectData.optString("teacher_name", "Not Allocated");

            int present = subjectData.optInt("present", 0);
            int total = subjectData.optInt("total", 0);
            double percentage = subjectData.optDouble("percentage", 0.0);

            // 2. Set Header: "Cellular Networks [ 304192 ]"
            holder.tvSubjectHeader.setText(name + " [ " + code + " ]");

            // 3. Set Teacher
            holder.tvTeacher.setText(teacher);

            // 4. Set Stats: "12 / 15 = 80.0%"
            // The backend guarantees 0.0 instead of NaN, but we double-check totals to be safe
            String statText;
            if (total == 0) {
                statText = "0 / 0 = 0%"; // Clean "Zero State"
            } else {
                statText = String.format("%d / %d = %.1f%%", present, total, percentage);
            }
            holder.tvStats.setText(statText);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getItemCount() {
        return data.length();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvSubjectHeader, tvTeacher, tvStats;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            // Ensure these IDs exist in item_attendance_subject.xml
            tvSubjectHeader = itemView.findViewById(R.id.tvSubjectHeader);
            tvTeacher = itemView.findViewById(R.id.tvTeacher);
            tvStats = itemView.findViewById(R.id.tvStats);
        }
    }
}