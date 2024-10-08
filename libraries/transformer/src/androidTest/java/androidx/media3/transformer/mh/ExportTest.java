/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.transformer.mh;

import static androidx.media3.common.util.Util.SDK_INT;
import static androidx.media3.transformer.AndroidTestUtil.FORCE_TRANSCODE_VIDEO_EFFECTS;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_4K60_PORTRAIT_FORMAT;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_4K60_PORTRAIT_URI_STRING;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_8K24_FORMAT;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_8K24_URI_STRING;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_BT2020_SDR;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_BT2020_SDR_FORMAT;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_SEF_H265_URI_STRING;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_SEF_URI_STRING;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_URI_STRING;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS_FORMAT;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS_URI_STRING;
import static androidx.media3.transformer.AndroidTestUtil.MP4_TRIM_OPTIMIZATION_PIXEL_URI_STRING;
import static androidx.media3.transformer.AndroidTestUtil.assumeFormatsSupported;
import static androidx.media3.transformer.AndroidTestUtil.recordTestSkipped;
import static androidx.media3.transformer.ExportResult.CONVERSION_PROCESS_TRANSMUXED_AND_TRANSCODED;
import static androidx.media3.transformer.ExportResult.OPTIMIZATION_SUCCEEDED;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Util;
import androidx.media3.effect.Presentation;
import androidx.media3.effect.ScaleAndRotateTransformation;
import androidx.media3.extractor.mp4.Mp4Extractor;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.test.utils.FakeExtractorOutput;
import androidx.media3.test.utils.FakeTrackOutput;
import androidx.media3.test.utils.TestUtil;
import androidx.media3.transformer.AndroidTestUtil.ForceEncodeEncoderFactory;
import androidx.media3.transformer.DefaultEncoderFactory;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportTestResult;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.TransformerAndroidTestRunner;
import androidx.media3.transformer.VideoEncoderSettings;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import java.io.File;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/** {@link Transformer} instrumentation tests. */
@RunWith(AndroidJUnit4.class)
public class ExportTest {
  private static final String TAG = "ExportTest";
  @Rule public final TestName testName = new TestName();

  private String testId;

  @Before
  public void setUpTestId() {
    testId = TAG + "_" + testName.getMethodName();
  }

  @Test
  public void export() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    // Note: throughout this class we only check decoding capability as tests should still run if
    // Transformer is able to succeed by falling back to a lower resolution.
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_FORMAT,
        /* outputFormat= */ null);
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(new ForceEncodeEncoderFactory(context))
            .build();
    MediaItem mediaItem =
        MediaItem.fromUri(Uri.parse(MP4_ASSET_WITH_INCREASING_TIMESTAMPS_URI_STRING));
    boolean skipCalculateSsim =
        (Util.SDK_INT < 33 && (Util.MODEL.equals("SM-F711U1") || Util.MODEL.equals("SM-F926U1")))
            || (Util.SDK_INT == 33 && Util.MODEL.equals("LE2121"));

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .setRequestCalculateSsim(!skipCalculateSsim)
            .build()
            .run(testId, mediaItem);

    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void exportWithoutDecodeEncode() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    Transformer transformer = new Transformer.Builder(context).build();
    MediaItem mediaItem =
        MediaItem.fromUri(Uri.parse(MP4_ASSET_WITH_INCREASING_TIMESTAMPS_URI_STRING));
    // No need to calculate SSIM because no decode/encoding, so input frames match output frames.

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, mediaItem);

    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void exportToSpecificBitrate() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_FORMAT,
        /* outputFormat= */ null);
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(
                new ForceEncodeEncoderFactory(
                    /* wrappedEncoderFactory= */ new DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(
                            new VideoEncoderSettings.Builder().setBitrate(5_000_000).build())
                        .build()))
            .build();
    MediaItem mediaItem =
        MediaItem.fromUri(Uri.parse(MP4_ASSET_WITH_INCREASING_TIMESTAMPS_URI_STRING));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setRemoveAudio(true).build();
    boolean skipCalculateSsim =
        (Util.SDK_INT < 33 && (Util.MODEL.equals("SM-F711U1") || Util.MODEL.equals("SM-F926U1")))
            || (Util.SDK_INT == 33 && Util.MODEL.equals("LE2121"));

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .setRequestCalculateSsim(!skipCalculateSsim)
            .build()
            .run(testId, editedMediaItem);

    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void export4K60() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_4K60_PORTRAIT_FORMAT,
        /* outputFormat= */ null);
    // Reference: b/262710361
    assumeFalse(
        "Skip due to over-reported encoder capabilities",
        Util.SDK_INT == 29 && Ascii.equalsIgnoreCase(Util.MODEL, "pixel 3"));
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(new ForceEncodeEncoderFactory(context))
            .build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_4K60_PORTRAIT_URI_STRING));
    boolean skipCalculateSsim = Util.SDK_INT < 30 && Util.DEVICE.equals("joyeuse");

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .setRequestCalculateSsim(!skipCalculateSsim)
            .setTimeoutSeconds(180)
            .build()
            .run(testId, mediaItem);

    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void export8K24() throws Exception {
    // Reference: b/244711282#comment5
    assumeFalse(
        "Some devices are capable of instantiating only either one 8K decoder or one 8K encoder",
        Ascii.equalsIgnoreCase(Util.MODEL, "tb-q706")
            || Ascii.equalsIgnoreCase(Util.MODEL, "sm-f916u1")
            || Ascii.equalsIgnoreCase(Util.MODEL, "sm-g981u1")
            || Ascii.equalsIgnoreCase(Util.MODEL, "le2121"));
    Context context = ApplicationProvider.getApplicationContext();
    assumeFormatsSupported(
        context, testId, /* inputFormat= */ MP4_ASSET_8K24_FORMAT, /* outputFormat= */ null);
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(new ForceEncodeEncoderFactory(context))
            .build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_8K24_URI_STRING));
    // TODO: b/281824052 - have requestCalculateSsim always be true after linked bug is fixed.
    boolean requestCalculateSsim = !Util.MODEL.equals("SM-G991B");

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .setRequestCalculateSsim(requestCalculateSsim)
            .setTimeoutSeconds(120)
            .build()
            .run(testId, mediaItem);

    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void export8K24_withDownscaling() throws Exception {
    // This test is to cover devices that are able to either decode or encode 8K, but not transcode.
    int downscaledWidth = 320;
    int downscaledHeight = 240;
    Context context = ApplicationProvider.getApplicationContext();
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_8K24_FORMAT,
        /* outputFormat= */ new Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H264)
            .setWidth(downscaledWidth)
            .setHeight(downscaledHeight)
            .build());

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .setTimeoutSeconds(120)
            .build()
            .run(
                testId,
                new EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(MP4_ASSET_8K24_URI_STRING)))
                    .setEffects(
                        new Effects(
                            /* audioProcessors= */ ImmutableList.of(),
                            /* videoEffects= */ ImmutableList.of(
                                Presentation.createForWidthAndHeight(
                                    downscaledWidth,
                                    downscaledHeight,
                                    Presentation.LAYOUT_SCALE_TO_FIT))))
                    .build());

    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void exportNoAudio() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_FORMAT,
        /* outputFormat= */ null);
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(new ForceEncodeEncoderFactory(context))
            .build();
    MediaItem mediaItem =
        MediaItem.fromUri(Uri.parse(MP4_ASSET_WITH_INCREASING_TIMESTAMPS_URI_STRING));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setRemoveAudio(true).build();
    boolean skipCalculateSsim =
        (Util.SDK_INT < 33 && (Util.MODEL.equals("SM-F711U1") || Util.MODEL.equals("SM-F926U1")))
            || (Util.SDK_INT == 33 && Util.MODEL.equals("LE2121"));

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .setRequestCalculateSsim(!skipCalculateSsim)
            .build()
            .run(testId, editedMediaItem);

    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void exportNoVideo() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(new ForceEncodeEncoderFactory(context))
            .build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(MP4_ASSET_URI_STRING)))
            .setRemoveVideo(true)
            .build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void exportSef() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    if (SDK_INT < 25) {
      // TODO(b/210593256): Remove test skipping after using an in-app muxer that supports B-frames
      //  before API 25.
      recordTestSkipped(context, testId, /* reason= */ "API version lacks muxing support");
      return;
    }
    Transformer transformer = new Transformer.Builder(context).build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(MP4_ASSET_SEF_URI_STRING)))
            .setFlattenForSlowMotion(true)
            .build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    assertThat(result.exportResult.durationMs).isGreaterThan(800);
    assertThat(result.exportResult.durationMs).isLessThan(950);
    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void exportSefH265() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    if (SDK_INT < 25) {
      // TODO(b/210593256): Remove test skipping after using an in-app muxer that supports B-frames
      //  before API 25.
      recordTestSkipped(context, testId, /* reason= */ "API version lacks muxing support");
      return;
    }
    Transformer transformer = new Transformer.Builder(context).build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(MP4_ASSET_SEF_H265_URI_STRING)))
            .setFlattenForSlowMotion(true)
            .build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void exportFrameRotation() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_FORMAT,
        /* outputFormat= */ null);
    Transformer transformer = new Transformer.Builder(context).build();
    MediaItem mediaItem =
        MediaItem.fromUri(Uri.parse(MP4_ASSET_WITH_INCREASING_TIMESTAMPS_URI_STRING));
    ImmutableList<Effect> videoEffects =
        ImmutableList.of(new ScaleAndRotateTransformation.Builder().setRotationDegrees(45).build());
    Effects effects = new Effects(/* audioProcessors= */ ImmutableList.of(), videoEffects);
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(effects).build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void exportTranscodeBt2020Sdr() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    // Reference: b/262732842#comment51
    if (SDK_INT <= 27 && Util.MANUFACTURER.equals("samsung")) {
      String reason = "Some older Samsung encoders report a non-specified error code";
      recordTestSkipped(context, testId, reason);
      throw new AssumptionViolatedException(reason);
    }
    assumeFormatsSupported(
        context, testId, /* inputFormat= */ MP4_ASSET_BT2020_SDR_FORMAT, /* outputFormat= */ null);
    Transformer transformer = new Transformer.Builder(context).build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_BT2020_SDR));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(FORCE_TRANSCODE_VIDEO_EFFECTS).build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void clippedMedia_trimOptimizationEnabled_pixel7Pro_completesWithOptimizationApplied()
      throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    // Devices with Tensor G2 & G3 chipsets should work, but Pixel 7a is flaky.
    assumeTrue(
        Ascii.toLowerCase(Util.MODEL).contains("pixel")
            && (Ascii.toLowerCase(Util.MODEL).contains("7")
                || Ascii.toLowerCase(Util.MODEL).contains("8")
                || Ascii.toLowerCase(Util.MODEL).contains("fold")
                || Ascii.toLowerCase(Util.MODEL).contains("tablet")));
    assumeFalse(Ascii.toLowerCase(Util.MODEL).contains("7a"));
    Transformer transformer =
        new Transformer.Builder(context).experimentalSetTrimOptimizationEnabled(true).build();
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(MP4_TRIM_OPTIMIZATION_PIXEL_URI_STRING)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(500)
                    .setEndPositionMs(1200)
                    .build())
            .build();
    EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(mediaItem).build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    Mp4Extractor mp4Extractor = new Mp4Extractor(new DefaultSubtitleParserFactory());
    FakeExtractorOutput fakeExtractorOutput =
        TestUtil.extractAllSamplesFromFilePath(mp4Extractor, result.filePath);
    FakeTrackOutput videoTrack = fakeExtractorOutput.trackOutputs.get(0);
    byte[] sps = videoTrack.lastFormat.initializationData.get(0);
    // Skip 7 bytes: NAL unit start code (4) and NAL unit type, profile, and reserved fields.
    int spsLevelIndex = 7;
    assertThat(result.exportResult.optimizationResult).isEqualTo(OPTIMIZATION_SUCCEEDED);
    assertThat(result.exportResult.durationMs).isAtMost(700);
    assertThat(result.exportResult.videoConversionProcess)
        .isEqualTo(CONVERSION_PROCESS_TRANSMUXED_AND_TRANSCODED);
    int higherVideoLevel = 41;
    assertThat(sps[spsLevelIndex]).isEqualTo(higherVideoLevel);
  }
}
