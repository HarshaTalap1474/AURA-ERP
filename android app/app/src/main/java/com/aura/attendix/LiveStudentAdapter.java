package com.aura.attendix;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.List;

/** Live monitor student row — green dot = present, grey = absent. */
public class LiveStudentAdapter extends RecyclerView.Adapter<LiveStudentAdapter.ViewHolder> {

    private final List<JSONObject> students;

    public LiveStudentAdapter(List<JSONObject> students) {
        this.students = students;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_live_student, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JSONObject student = students.get(position);
        holder.tvRoll.setText(student.optString("roll_no", "--"));
        holder.tvName.setText(student.optString("name", "Unknown"));
        boolean present = student.optBoolean("is_present", false);
        holder.tvPresenceIndicator.setText(present ? "●" : "●");
        holder.tvPresenceIndicator.setTextColor(present ? 0xFF10B981 : 0xFFEF4444);
        holder.tvPresenceLabel.setText(present ? "PRESENT" : "ABSENT");
        holder.tvPresenceLabel.setTextColor(present ? 0xFF10B981 : 0xFFEF4444);
    }

    @Override
    public int getItemCount() { return students.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvRoll, tvName, tvPresenceIndicator, tvPresenceLabel;
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRoll = itemView.findViewById(R.id.tvRoll);
            tvName = itemView.findViewById(R.id.tvName);
            tvPresenceIndicator = itemView.findViewById(R.id.tvPresenceIndicator);
            tvPresenceLabel     = itemView.findViewById(R.id.tvPresenceLabel);
        }
    }
}
