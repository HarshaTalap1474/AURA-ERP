package com.aura.attendix;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.List;

/** Parent's children attendance summary adapter. */
public class ChildrenAdapter extends RecyclerView.Adapter<ChildrenAdapter.ViewHolder> {

    private final List<JSONObject> children;

    public ChildrenAdapter(List<JSONObject> children) { this.children = children; }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_child_attendance, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JSONObject child = children.get(position);
        holder.tvName.setText(child.optString("name", "Student"));
        holder.tvRoll.setText(child.optString("roll_no", "--"));
        holder.tvDept.setText(child.optString("department", "--") + " • " + child.optString("semester", ""));

        double pct = child.optDouble("attendance_percentage", 0.0);
        holder.tvPercentage.setText(String.format("%.1f%%", pct));
        holder.progressBar.setProgress((int) pct);

        boolean atRisk = child.optBoolean("is_at_risk", false);
        holder.tvRiskLabel.setVisibility(atRisk ? View.VISIBLE : View.GONE);
        holder.tvPercentage.setTextColor(atRisk ? 0xFFEF4444 : 0xFF10B981);
    }

    @Override
    public int getItemCount() { return children.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvRoll, tvDept, tvPercentage, tvRiskLabel;
        ProgressBar progressBar;
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName      = itemView.findViewById(R.id.tvChildName);
            tvRoll      = itemView.findViewById(R.id.tvChildRoll);
            tvDept      = itemView.findViewById(R.id.tvChildDept);
            tvPercentage = itemView.findViewById(R.id.tvAttendancePct);
            progressBar = itemView.findViewById(R.id.progressAttendance);
            tvRiskLabel = itemView.findViewById(R.id.tvAtRisk);
        }
    }
}
