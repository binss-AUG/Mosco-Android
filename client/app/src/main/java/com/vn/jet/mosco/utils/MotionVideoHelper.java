package com.vn.jet.mosco.utils;

import android.content.Context;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import com.vn.jet.mosco.MoscoApplication;

import java.util.LinkedList;

/**
 * MotionVideoHelper - Lớp tiện ích quản lý trình phát video ExoPlayer cho Thẻ bài chuyển động (Motion Cards).
 * Giúp tối ưu hóa tài nguyên hệ thống, tái sử dụng mã nguồn và tuân thủ nguyên lý DRY (Don't Repeat Yourself).
 */
public class MotionVideoHelper {

    private static final int MAX_POOL_SIZE = 3;
    private static final LinkedList<ExoPlayer> activePlayers = new LinkedList<>();

    /**
     * Khởi tạo, cấu hình và chuẩn bị một đối tượng ExoPlayer phát video mượt mà trên TextureView.
     *
     * @param context            Ngữ cảnh ứng dụng hoặc Activity.
     * @param textureView        TextureView dùng để hiển thị luồng video.
     * @param videoUrl           Đường dẫn URL của video thẻ bài cần phát.
     * @param fallbackImageView   ImageView chứa ảnh tĩnh mặt trước làm ảnh đệm trước khi video sẵn sàng.
     * @return Trả về đối tượng ExoPlayer đã được cấu hình và chuẩn bị.
     */
    @Nullable
    public static ExoPlayer playMotionVideo(@NonNull Context context,
                                            @NonNull TextureView textureView,
                                            @Nullable String videoUrl,
                                            @Nullable ImageView fallbackImageView) {
        if (videoUrl == null || videoUrl.isEmpty()) {
            textureView.setVisibility(View.GONE);
            return null;
        }

        textureView.setVisibility(View.GONE);

        try {
            // Tối ưu RAM cho Giả lập Android 9: Giảm buffer từ 10s/50s xuống 1.5s/3s để chống OOM cho file video lớn 36MB
            DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(1_500, 3_000, 500, 500)
                    .build();

            ExoPlayer player = new ExoPlayer.Builder(context)
                    .setLoadControl(loadControl)
                    .build();

            // Quản lý Pool: Giữ tối đa 3 player hoạt động cùng lúc
            synchronized (activePlayers) {
                if (activePlayers.size() >= MAX_POOL_SIZE) {
                    ExoPlayer oldest = activePlayers.poll();
                    if (oldest != null) {
                        oldest.release();
                    }
                }
                activePlayers.add(player);
            }

            player.setVideoTextureView(textureView);
            player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
            player.setRepeatMode(Player.REPEAT_MODE_ONE);
            // Không tự phát ngay để tránh giật lag khung hình đen lúc khởi tạo
            player.setPlayWhenReady(false);

            MediaItem mediaItem = MediaItem.fromUri(videoUrl);
            DataSource.Factory cacheDataSourceFactory = MoscoApplication.getCacheDataSourceFactory(context);
            MediaSource mediaSource = new ProgressiveMediaSource.Factory(cacheDataSourceFactory)
                    .createMediaSource(mediaItem);

            player.setMediaSource(mediaSource);
            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_READY) {
                        player.play();
                        textureView.setVisibility(View.VISIBLE);
                        textureView.setAlpha(1.0f);
                        
                        // Đặt cờ báo hiệu video đã sẵn sàng để logic lật thẻ không bật lại ảnh tĩnh đè lên video
                        textureView.setTag(com.vn.jet.mosco.R.id.vv_objet_detail_video, true);
                        
                        // Độ trễ 200ms giúp ẩn ảnh đệm tĩnh sau khi video đã vẽ thành công frame đầu tiên
                        textureView.postDelayed(() -> {
                            if (fallbackImageView != null) {
                                fallbackImageView.setVisibility(View.INVISIBLE);
                            }
                        }, 200);
                    }
                }

                @Override
                public void onPlayerError(@NonNull PlaybackException error) {
                    error.printStackTrace();
                    if (fallbackImageView != null) {
                        fallbackImageView.setVisibility(View.VISIBLE);
                    }
                    textureView.setVisibility(View.GONE);
                }
            });

            player.prepare();
            return player;

        } catch (Exception e) {
            e.printStackTrace();
            if (fallbackImageView != null) {
                fallbackImageView.setVisibility(View.VISIBLE);
            }
            textureView.setVisibility(View.GONE);
            return null;
        }
    }

    /**
     * Giải phóng player một cách an toàn và loại khỏi danh sách quản lý.
     * @param player Đối tượng ExoPlayer cần giải phóng
     */
    public static void releasePlayer(ExoPlayer player) {
        if (player != null) {
            synchronized (activePlayers) {
                activePlayers.remove(player);
            }
            player.release();
        }
    }
}
