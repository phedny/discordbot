package net.phedny.decryptobot

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.Permission
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.*
import java.io.*

class SheetsClient {

    private val JSON_FACTORY = JacksonFactory.getDefaultInstance()
    private val APPLICATION_NAME = "Decrypto Bot"

    /**
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private val SCOPES: List<String> = listOf(DriveScopes.DRIVE_FILE, SheetsScopes.SPREADSHEETS)
    private val CREDENTIALS_FILE_PATH = "/credentials.json"
    private val TOKENS_DIRECTORY_PATH = "tokens"
    private val TEMPLATE_SPREADSHEET_ID = "1KXP68tPMVIf_Il0RLe55R4xuPmfdfl0B_VUIJ71q4wg"

    /**
     * Creates an authorized Credential object.
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    @Throws(IOException::class)
    private fun getCredentials(HTTP_TRANSPORT: NetHttpTransport): Credential? {
        // Load client secrets.
        val `in`: InputStream = SheetsClient::class.java.getResourceAsStream(CREDENTIALS_FILE_PATH)
            ?: throw FileNotFoundException("Resource not found: $CREDENTIALS_FILE_PATH")
        val clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, InputStreamReader(`in`))

        // Build flow and trigger user authorization request.
        val flow = GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
            .setDataStoreFactory(FileDataStoreFactory(File(TOKENS_DIRECTORY_PATH)))
            .setAccessType("offline")
            .build()
        val receiver: LocalServerReceiver = LocalServerReceiver.Builder().setPort(8888).build()
        return AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
    }

    private val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()
    private val driveService = Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
        .setApplicationName(APPLICATION_NAME)
        .build()
    private val sheetsService = Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
        .setApplicationName(APPLICATION_NAME)
        .build()

    fun initializeNewSpreadsheet(): String {
        val spreadsheet = Spreadsheet()
        spreadsheet.properties = SpreadsheetProperties().setTitle("Decrypto game")
        val spreadsheetId = sheetsService.spreadsheets().create(spreadsheet)
            .setFields("spreadsheetId")
            .execute()
            .spreadsheetId

        val copyRequest = CopySheetToAnotherSpreadsheetRequest()
        copyRequest.destinationSpreadsheetId = spreadsheetId
        val copyResult = sheetsService.spreadsheets().sheets().copyTo(TEMPLATE_SPREADSHEET_ID, 0, copyRequest).execute()
        println("Copy request: " + copyResult)


        val batchUpdateRequest = BatchUpdateSpreadsheetRequest()
        batchUpdateRequest.requests = listOf(
            Request().setDeleteSheet(DeleteSheetRequest().setSheetId(0)),
            Request().setUpdateSheetProperties(UpdateSheetPropertiesRequest().setProperties(SheetProperties().setSheetId(copyResult.sheetId).setTitle("Decrypto game")).setFields("title")),
            Request().setAddProtectedRange(AddProtectedRangeRequest().setProtectedRange(ProtectedRange().setDescription("HiddenData").setRange(GridRange().setSheetId(copyResult.sheetId).setStartRowIndex(39).setEndRowIndex(139)).setEditors(Editors().setUsers(listOf("decryptobot@gmail.com"))))),
            Request().setUpdateDimensionProperties(UpdateDimensionPropertiesRequest().setRange(DimensionRange().setSheetId(copyResult.sheetId).setDimension("ROWS").setStartIndex(39).setEndIndex(139)).setProperties(DimensionProperties().setHiddenByUser(true)).setFields("hiddenByUser"))
        )
        println("Batch request: " + sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchUpdateRequest).execute())

        println("Make public request: " + driveService.Permissions().create(spreadsheetId, Permission().setType("anyone").setRole("writer")).execute())

        return spreadsheetId
    }

    fun writeGameInfo(spreadsheetId: String, opponentSpreadsheetId: String, guildId: String, channelId: String, color: String, players: List<String>, secretWords: List<String>) {
        val (codewordsRange1, codewordsRange2) = when(color) {
            "BLACK" -> Pair("M22", "R22")
            "WHITE" -> Pair("B22", "G22")
            else    -> throw IllegalAccessException("Unknown color: $color")
        }

        val data = listOf(
            ValueRange()
                .setRange("B70")
                .setValues(
                    listOf(
                        listOf(opponentSpreadsheetId),
                        listOf(guildId),
                        listOf(channelId),
                        listOf(color)
                    )
                ),
            ValueRange()
                .setRange("G70")
                .setValues(players.map { listOf(it) }),
            ValueRange()
                .setRange(codewordsRange1)
                .setValues(listOf(secretWords.take(2).mapIndexed { i, s -> "\uD83D\uDD11 ${i + 1} -- $s" })),
            ValueRange()
                .setRange(codewordsRange2)
                .setValues(listOf(secretWords.drop(2).mapIndexed { i, s -> "\uD83D\uDD11 ${i + 3} -- $s" }))
        )

        val request = BatchUpdateValuesRequest()
            .setValueInputOption("RAW")
            .setData(data)

        println("Batch update: " + sheetsService.spreadsheets().values().batchUpdate(spreadsheetId, request).execute())
    }

}