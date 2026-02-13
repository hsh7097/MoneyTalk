package com.sanha.moneytalk.core.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 구글 드라이브 연동 헬퍼
 */
@Singleton
class GoogleDriveHelper @Inject constructor() {

    private var driveService: Drive? = null

    companion object {
        private const val APP_FOLDER_NAME = "MoneyTalk Backup"
        private const val MIME_TYPE_JSON = "application/json"
        private const val MIME_TYPE_CSV = "text/csv"
        private const val MIME_TYPE_FOLDER = "application/vnd.google-apps.folder"
    }

    /**
     * 구글 로그인 클라이언트 생성
     */
    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()

        return GoogleSignIn.getClient(context, signInOptions)
    }

    /**
     * 구글 로그인 Intent 가져오기
     */
    fun getSignInIntent(context: Context): Intent {
        return getGoogleSignInClient(context).signInIntent
    }

    /**
     * 현재 로그인된 계정 확인
     */
    fun getSignedInAccount(context: Context): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    /**
     * 로그인 상태 확인 (계정 존재 여부만 체크)
     * hasPermissions가 false를 반환해도 실제로는 scope가 부여되어 있는 경우가 있으므로
     * 계정이 있으면 true로 판단하고, Drive API 호출 시 에러가 나면 그때 재인증
     */
    fun isSignedIn(context: Context): Boolean {
        val account = getSignedInAccount(context)
        return account != null
    }

    /**
     * Drive 서비스가 초기화되어 있는지 확인
     */
    fun isDriveServiceReady(): Boolean = driveService != null

    /**
     * Silent Sign-In 시도
     * 이미 로그인된 계정이 있으면 UI 없이 자동 로그인 (Drive 서비스도 초기화)
     * @return 성공한 계정, 실패 시 null
     */
    suspend fun trySilentSignIn(context: Context): GoogleSignInAccount? {
        return try {
            val task = getGoogleSignInClient(context).silentSignIn()

            // 이미 완료된 경우 (캐시된 계정)
            if (task.isSuccessful) {
                val account = task.result
                if (account != null) {
                    initializeDriveService(context, account)
                    Log.d("GoogleDriveHelper", "Silent sign-in 즉시 성공: ${account.email}")
                }
                return account
            }

            // 비동기 완료 대기
            suspendCoroutine { cont ->
                task.addOnSuccessListener { account ->
                    if (account != null) {
                        initializeDriveService(context, account)
                        Log.d("GoogleDriveHelper", "Silent sign-in 성공: ${account.email}")
                    }
                    cont.resume(account)
                }.addOnFailureListener { e ->
                    Log.d("GoogleDriveHelper", "Silent sign-in 실패: ${e.message}")
                    cont.resume(null)
                }
            }
        } catch (e: Exception) {
            Log.d("GoogleDriveHelper", "Silent sign-in 예외: ${e.message}")
            null
        }
    }

    /**
     * 로그아웃
     */
    suspend fun signOut(context: Context) = withContext(Dispatchers.IO) {
        getGoogleSignInClient(context).signOut()
        driveService = null
    }

    /**
     * Drive 서비스 초기화
     */
    fun initializeDriveService(context: Context, account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = account.account

        driveService = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("MoneyTalk")
            .build()
    }

    /**
     * 앱 폴더 찾기 또는 생성
     */
    private suspend fun getOrCreateAppFolder(): String = withContext(Dispatchers.IO) {
        val drive = driveService ?: throw IllegalStateException("Drive service not initialized")

        // 기존 폴더 찾기
        val query =
            "name = '$APP_FOLDER_NAME' and mimeType = '$MIME_TYPE_FOLDER' and trashed = false"
        val result: FileList = drive.files().list()
            .setQ(query)
            .setSpaces("drive")
            .execute()

        if (result.files.isNotEmpty()) {
            return@withContext result.files[0].id
        }

        // 새 폴더 생성
        val folderMetadata = File().apply {
            name = APP_FOLDER_NAME
            mimeType = MIME_TYPE_FOLDER
        }

        val folder = drive.files().create(folderMetadata)
            .setFields("id")
            .execute()

        folder.id
    }

    /**
     * 파일 업로드
     */
    suspend fun uploadFile(
        fileName: String,
        content: String,
        format: ExportFormat
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val drive = driveService ?: throw IllegalStateException("Drive service not initialized")
            val folderId = getOrCreateAppFolder()

            val mimeType = when (format) {
                ExportFormat.JSON -> MIME_TYPE_JSON
                ExportFormat.CSV -> MIME_TYPE_CSV
            }

            val fileMetadata = File().apply {
                name = fileName
                parents = listOf(folderId)
            }

            val mediaContent = ByteArrayContent(mimeType, content.toByteArray(Charsets.UTF_8))

            val file = drive.files().create(fileMetadata, mediaContent)
                .setFields("id, name, webViewLink")
                .execute()

            Result.success(file.webViewLink ?: file.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 백업 파일 목록 가져오기
     */
    suspend fun listBackupFiles(): Result<List<DriveBackupFile>> = withContext(Dispatchers.IO) {
        try {
            val drive = driveService ?: throw IllegalStateException("Drive service not initialized")
            val folderId = getOrCreateAppFolder()

            val query = "'$folderId' in parents and trashed = false"
            val result: FileList = drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name, createdTime, size)")
                .setOrderBy("createdTime desc")
                .execute()

            val files = result.files.map { file ->
                DriveBackupFile(
                    id = file.id,
                    name = file.name,
                    createdTime = file.createdTime?.value ?: 0L,
                    size = file.getSize()?.toLong() ?: 0L
                )
            }

            Result.success(files)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 파일 다운로드
     */
    suspend fun downloadFile(fileId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val drive = driveService ?: throw IllegalStateException("Drive service not initialized")

            val outputStream = ByteArrayOutputStream()
            drive.files().get(fileId)
                .executeMediaAndDownloadTo(outputStream)

            val content = outputStream.toString(Charsets.UTF_8.name())
            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 파일 삭제
     */
    suspend fun deleteFile(fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val drive = driveService ?: throw IllegalStateException("Drive service not initialized")
            drive.files().delete(fileId).execute()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * 드라이브 백업 파일 정보
 */
data class DriveBackupFile(
    val id: String,
    val name: String,
    val createdTime: Long,
    val size: Long
)
