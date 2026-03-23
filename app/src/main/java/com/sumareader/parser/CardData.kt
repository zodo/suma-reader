package com.sumareader.parser

data class SumaCard(
    val uid: String,
    val serialNumber: String,
    val holderName: String,
    val holderSurname: String,
    val cardExpiry: String,
    val cardType: Int,
    val cardSubtype: Int,
    val enterprise: Int,
    val title: TitleInfo?,
    val tuinBalanceCents: Int?, // TUIN cards have a euro balance
    val recharges: List<RechargeInfo>,
    val currentValidation: CurrentValidation?,
    val validations: List<ValidationRecord>,
)

data class TitleInfo(
    val code: Int,
    val name: String,
    val controlTariff: Int,
    val zone: String,
    val validityDate: String,
    val tripBalance: Int,
)

data class RechargeInfo(
    val titleName: String,
    val date: String,
    val amountCents: Int,
)

data class CurrentValidation(
    val titleName: String,
    val zone: String,
    val operator: String,
    val typeName: String,
    val station: Int,
    val vestibule: Int,
    val dateTime: String,
    val passengers: Int,
    val transferPassengers: Int,
    val externalTransfers: Int,
)

data class ValidationRecord(
    val titleName: String,
    val dateTime: String,
    val dateRaw: Int, // for sorting
    val typeName: String,
    val station: Int,
    val vestibule: Int,
    val zone: String,
    val unitsConsumed: Int,
    val operator: String,
)
