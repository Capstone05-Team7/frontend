package com.example.capstone07

import android.app.Activity
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.capstone07.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initBottomNavigation()
    }

    private fun initBottomNavigation() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_container_frl, HomeFragment())
            .commit()
        binding.mainBtmBtm.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.btm_outline_home_xml -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.main_container_frl, HomeFragment())
                        .commit()
                    return@setOnItemSelectedListener true
                }
                R.id.btm_outline_analysis_xml -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.main_container_frl, ScriptFragment())
                        .commit()
                    return@setOnItemSelectedListener true
                }
                R.id.btm_outline_script_xml -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.main_container_frl, ScriptFragment())
                        .commit()
                    return@setOnItemSelectedListener true
                }
            }
            false
        }
    }
}