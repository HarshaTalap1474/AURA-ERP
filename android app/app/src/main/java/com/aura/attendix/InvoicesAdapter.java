package com.aura.attendix;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.List;

/** Fee invoice card adapter. */
public class InvoicesAdapter extends RecyclerView.Adapter<InvoicesAdapter.ViewHolder> {

    private final List<JSONObject> invoices;
    private final Context context;

    public InvoicesAdapter(List<JSONObject> invoices, Context context) {
        this.invoices = invoices;
        this.context  = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_fee_invoice, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JSONObject invoice = invoices.get(position);
        holder.tvTitle.setText(invoice.optString("title", "Fee Invoice"));
        holder.tvAmount.setText("₹ " + invoice.optString("amount", "0.00"));
        holder.tvDueDate.setText("Due: " + invoice.optString("due_date", "N/A"));

        boolean isPaid = invoice.optBoolean("is_paid", false);
        holder.tvPaidStatus.setText(isPaid ? "✅ PAID" : "⚠️ UNPAID");
        holder.tvPaidStatus.setTextColor(isPaid ? 0xFF10B981 : 0xFFEF4444);
        holder.btnPay.setVisibility(isPaid ? View.GONE : View.VISIBLE);

        String studentName = invoice.optString("student_name", "");
        if (!studentName.isEmpty()) {
            holder.tvStudentName.setVisibility(View.VISIBLE);
            holder.tvStudentName.setText(studentName);
        } else {
            holder.tvStudentName.setVisibility(View.GONE);
        }

        // Payment stub — real integration would open Razorpay SDK
        holder.btnPay.setOnClickListener(v ->
            Toast.makeText(context, "Payment gateway coming soon!", Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    public int getItemCount() { return invoices.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvAmount, tvDueDate, tvPaidStatus, tvStudentName;
        Button btnPay;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle       = itemView.findViewById(R.id.tvInvoiceTitle);
            tvAmount      = itemView.findViewById(R.id.tvAmount);
            tvDueDate     = itemView.findViewById(R.id.tvDueDate);
            tvPaidStatus  = itemView.findViewById(R.id.tvPaidStatus);
            tvStudentName = itemView.findViewById(R.id.tvStudentName);
            btnPay        = itemView.findViewById(R.id.btnPay);
        }
    }
}
