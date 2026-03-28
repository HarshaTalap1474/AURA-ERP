package com.aura.attendix;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.List;

/** Adapter for TimetableActivity — shows subject, time, room, status badge. */
public class TimetableAdapter extends RecyclerView.Adapter<TimetableAdapter.ViewHolder> {

    public interface OnSlotClickListener {
        void onSlotClick(JSONObject slot);
    }

    private final List<JSONObject> slots;
    private final OnSlotClickListener listener;

    public TimetableAdapter(List<JSONObject> slots, OnSlotClickListener listener) {
        this.slots = slots;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_timetable_slot, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JSONObject slot = slots.get(position);
        holder.tvSubject.setText(slot.optString("course_name", "--"));
        holder.tvCode.setText(slot.optString("course_code", ""));
        holder.tvTime.setText(slot.optString("start_time", "--") + " – " + slot.optString("end_time", "--"));
        holder.tvRoom.setText("📍 " + slot.optString("room", "TBD"));

        String status = slot.optString("status", "UPCOMING");
        holder.tvStatus.setText(status);
        int color;
        switch (status) {
            case "ACTIVE":   color = 0xFF10B981; break; // green
            case "FINISHED": color = 0xFF9CA3AF; break; // grey
            default:         color = 0xFF6366F1; break; // indigo
        }
        holder.tvStatus.setBackgroundColor(color);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onSlotClick(slot);
        });
    }

    @Override
    public int getItemCount() { return slots.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvSubject, tvCode, tvTime, tvRoom, tvStatus;
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSubject = itemView.findViewById(R.id.tvSubject);
            tvCode    = itemView.findViewById(R.id.tvCode);
            tvTime    = itemView.findViewById(R.id.tvTime);
            tvRoom    = itemView.findViewById(R.id.tvRoom);
            tvStatus  = itemView.findViewById(R.id.tvStatus);
        }
    }
}
