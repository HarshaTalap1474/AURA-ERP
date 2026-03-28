package com.aura.attendix;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.List;

/**
 * LeaveAdapter — handles both pending (with action buttons) and
 * processed (read-only) leave requests.
 */
public class LeaveAdapter extends RecyclerView.Adapter<LeaveAdapter.ViewHolder> {

    public interface OnLeaveAction {
        void onAction(int requestId, String action);
    }

    private final List<JSONObject> items;
    private final boolean showActions;
    private final OnLeaveAction listener;

    public LeaveAdapter(List<JSONObject> items, boolean showActions, @Nullable OnLeaveAction listener) {
        this.items       = items;
        this.showActions = showActions;
        this.listener    = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_leave_request, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JSONObject item = items.get(position);
        int id = item.optInt("id", -1);

        holder.tvStudentName.setText(item.optString("student_name", "Student"));
        holder.tvRoll.setText(item.optString("student_roll", "--"));
        holder.tvType.setText(item.optString("leave_type", "--"));
        holder.tvDates.setText(item.optString("start_date", "--") + " → " + item.optString("end_date", "--"));
        holder.tvReason.setText(item.optString("reason", "--"));
        holder.tvAppliedOn.setText("Applied: " + item.optString("applied_on", "--"));

        String status = item.optString("status", "PENDING");
        holder.tvStatus.setText(status);
        switch (status) {
            case "APPROVED": holder.tvStatus.setTextColor(0xFF10B981); break;
            case "REJECTED": holder.tvStatus.setTextColor(0xFFEF4444); break;
            default:         holder.tvStatus.setTextColor(0xFFF59E0B); break;
        }

        if (showActions && listener != null) {
            holder.btnApprove.setVisibility(View.VISIBLE);
            holder.btnReject.setVisibility(View.VISIBLE);
            holder.btnApprove.setOnClickListener(v -> listener.onAction(id, "approve"));
            holder.btnReject.setOnClickListener(v -> listener.onAction(id, "reject"));
        } else {
            holder.btnApprove.setVisibility(View.GONE);
            holder.btnReject.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvStudentName, tvRoll, tvType, tvDates, tvReason, tvAppliedOn, tvStatus;
        Button btnApprove, btnReject;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvStudentName = itemView.findViewById(R.id.tvStudentName);
            tvRoll        = itemView.findViewById(R.id.tvRoll);
            tvType        = itemView.findViewById(R.id.tvLeaveType);
            tvDates       = itemView.findViewById(R.id.tvDates);
            tvReason      = itemView.findViewById(R.id.tvReason);
            tvAppliedOn   = itemView.findViewById(R.id.tvAppliedOn);
            tvStatus      = itemView.findViewById(R.id.tvStatus);
            btnApprove    = itemView.findViewById(R.id.btnApprove);
            btnReject     = itemView.findViewById(R.id.btnReject);
        }
    }
}
