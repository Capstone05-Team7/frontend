package com.example.capstone07

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.capstone07.databinding.FragmentInputBinding

class InputFragment : Fragment() {

    private var _binding: FragmentInputBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInputBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 확인 버튼 클릭 처리
        binding.buttonCheck.setOnClickListener {
            handleCheckButtonClick()
        }
    }

    private fun handleCheckButtonClick() {
        // 발표 시작 준비 화면으로 이동
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}