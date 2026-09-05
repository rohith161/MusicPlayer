package com.rohith161.musicplayer

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : AppCompatActivity() {
    private lateinit var repository: MusicRepository
    private lateinit var adapter: SongAdapter
    private lateinit var songList: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private var allTracks: List<Track> = emptyList()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { loadLibraryIfPermitted() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = MusicRepository(contentResolver)
        songList = findViewById(R.id.songList)
        emptyState = findViewById(R.id.emptyState)
        val searchInput: EditText = findViewById(R.id.searchInput)
        val grantButton: Button = findViewById(R.id.grantPermissionButton)

        adapter = SongAdapter { playTrack(it) }
        songList.layoutManager = LinearLayoutManager(this)
        songList.adapter = adapter

        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim().orEmpty()
                adapter.submitList(if (query.isBlank()) allTracks else allTracks.filter {
                    it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)
                })
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })

        grantButton.setOnClickListener { requestAudioPermissions() }

        connectToPlaybackService()
        loadLibraryIfPermitted()
    }

    private fun connectToPlaybackService() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture.addListener({
            try {
                controller = controllerFuture.get()
            } catch (_: Exception) {
                Toast.makeText(this, "Unable to start playback service", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun loadLibraryIfPermitted() {
        if (!hasAudioPermission()) {
            emptyState.visibility = android.view.View.VISIBLE
            songList.visibility = android.view.View.GONE
            return
        }

        Thread {
            val tracks = repository.loadTracks()
            runOnUiThread {
                allTracks = tracks
                adapter.submitList(tracks)
                emptyState.visibility = if (tracks.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                songList.visibility = if (tracks.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
            }
        }.start()
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermissions() {
        val permissions = buildList {
            add(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun playTrack(track: Track) {
        val player = controller ?: run {
            Toast.makeText(this, "Player is still starting", Toast.LENGTH_SHORT).show()
            return
        }
        val items = allTracks.map { item ->
            MediaItem.Builder()
                .setMediaId(item.id.toString())
                .setUri(item.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtist(item.artist)
                        .setAlbumTitle(item.album)
                        .build()
                )
                .build()
        }
        val index = allTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        player.setMediaItems(items, index, 0L)
        player.prepare()
        player.play()
    }

    override fun onDestroy() {
        controller?.release()
        controller = null
        super.onDestroy()
    }
}
