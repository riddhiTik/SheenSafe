package org.kashmir.heatguard.data

interface ContactRepository {
    fun getEmergencyContactName(): String
    fun getEmergencyContactPhone(): String
    fun setEmergencyContact(name: String, phone: String)
}
