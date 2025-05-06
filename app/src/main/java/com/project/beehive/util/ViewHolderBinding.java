package com.project.beehive.util;

import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

public class ViewHolderBinding extends RecyclerView.ViewHolder {
    public ViewBinding binding;

    public ViewHolderBinding(ViewBinding binding) {
        super(binding.getRoot());
        this.binding = binding;
    }
}
