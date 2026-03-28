package com.aura.attendix;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.List;

/** Student's own leave history adapter with coloured status chips. */
public class LeaveHistoryAdapter extends RecyclerView.Adapter<LeaveHistoryAdapter.ViewHolder> {

    private final List<JSONObject> leaves;

    public LeaveHistoryAdapter(List<JSONObject> leaves) { this.leaves = leaves; }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_leave_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JSONObject item = leaves.get(position);
        holder.tvType.setText(item.optString("leave_type", "--"));
        holder.tvDates.setText(item.optString("start_date", "--") + " → " + item.optString("end_date", "--"));
        holder.tvReason.setText(item.optString("reason", "--"));
        holder.tvApplied.setText("Applied: " + item.optString("applied_on", "--"));

        String status = item.optString("status", "PENDING");
        holder.tvStatus.setText(status);
        switch (status) {
            case "APPROVED": holder.tvStatus.setBackgroundColor(0xFF10B981); break;
            case "REJECTED": holder.tvStatus.setBackgroundColor(0xFFEF4444); break;
            default:         holder.tvStatus.setBackgroundColor(0xFFF59E0B); break;
        }
    }

    @Override
    public int getItemCount() { return leaves.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvType, tvDates, tvReason, tvApplied, tvStatus;
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvType    = itemView.findViewById(R.id.tvLeaveType);
            tvDates   = itemView.findViewById(R.id.tvDates);
            tvReason  = itemView.findViewById(R.id.tvReason);
            tvApplied = itemView.findViewById(R.id.tvAppliedOn);
            tvStatus  = itemView.findViewById(R.id.tvStatus);
        }
    }
}
