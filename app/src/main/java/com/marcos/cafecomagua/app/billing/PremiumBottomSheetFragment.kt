package com.marcos.cafecomagua.app.billing

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.marcos.cafecomagua.databinding.FragmentPremiumBottomSheetBinding

class PremiumBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentPremiumBottomSheetBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPremiumBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonSubscribeTrial.setOnClickListener {
            // Lança a tela de assinatura principal
            startActivity(Intent(requireContext(), SubscriptionActivity::class.java))
            dismiss() // Fecha o bottom sheet
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "PremiumBottomSheet"
    }
}