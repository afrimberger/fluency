/*
 * Copyright 2019 Mitsunori Komatsu (komamitsu)
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

package org.komamitsu.fluency.treasuredata;

import com.treasuredata.client.TDClient;
import com.treasuredata.client.TDClientConfig;
import com.treasuredata.client.TDHttpClient;
import org.junit.jupiter.api.Test;
import org.komamitsu.fluency.Fluency;
import org.komamitsu.fluency.buffer.DefaultBuffer;
import org.komamitsu.fluency.flusher.Flusher;
import org.komamitsu.fluency.treasuredata.ingester.sender.TreasureDataSender;

import java.io.IOException;
import java.lang.reflect.Field;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

public class FluencyBuilderForTreasureDataTest
{
    private static final String APIKEY = "12345/1qaz2wsx3edc4rfv5tgb6yhn";

    private void assertBuffer(DefaultBuffer buffer)
    {
        assertThat(buffer.getMaxBufferSize(), is(512 * 1024 * 1024L));
        assertThat(buffer.getFileBackupDir(), is(nullValue()));
        assertThat(buffer.bufferFormatType(), is("packed_forward"));
        assertThat(buffer.getChunkExpandRatio(), is(2f));
        assertThat(buffer.getChunkRetentionSize(), is(64 * 1024 * 1024));
        assertThat(buffer.getChunkInitialSize(), is(4 * 1024 * 1024));
        assertThat(buffer.getChunkRetentionTimeMillis(), is(30000));
        assertThat(buffer.getJvmHeapBufferMode(), is(false));
    }

    private void assertFlusher(Flusher flusher)
    {
        assertThat(flusher.isTerminated(), is(false));
        assertThat(flusher.getFlushAttemptIntervalMillis(), is(600));
        assertThat(flusher.getWaitUntilBufferFlushed(), is(60));
        assertThat(flusher.getWaitUntilTerminated(), is(60));
    }

    private void assertDefaultFluentdSender(
            TreasureDataSender sender,
            String expectedEndpoint,
            boolean expectedUseSsl,
            String expectedApiKey)
            throws NoSuchFieldException, IllegalAccessException
    {
        assertThat(sender.getRetryInternalMs(), is(1000));
        assertThat(sender.getMaxRetryInternalMs(), is(30000));
        assertThat(sender.getRetryFactor(), is(2.0f));
        assertThat(sender.getRetryMax(), is(10));
        assertThat(sender.getWorkBufSize(), is(8192));

        Field httpClientField = TDClient.class.getDeclaredField("httpClient");
        httpClientField.setAccessible(true);
        TDHttpClient tdHttpClient = (TDHttpClient) httpClientField.get(sender.getClient());

        Field configField = TDHttpClient.class.getDeclaredField("config");
        configField.setAccessible(true);
        TDClientConfig config = (TDClientConfig) configField.get(tdHttpClient);

        assertThat(config.endpoint, is(expectedEndpoint));
        assertThat(config.useSSL, is(expectedUseSsl));
        assertThat(config.apiKey.get(), is(expectedApiKey));
    }

    @Test
    public void build()
            throws IOException, NoSuchFieldException, IllegalAccessException
    {
        try (Fluency fluency = new FluencyBuilderForTreasureData().build(APIKEY)) {
            assertBuffer((DefaultBuffer) fluency.getBuffer());
            assertFlusher(fluency.getFlusher());
            assertDefaultFluentdSender(
                    (TreasureDataSender) fluency.getFlusher().getIngester().getSender(),
                    "api-import.treasuredata.com", true, APIKEY);
        }
    }

    @Test
    public void buildWithCustomHttpsEndpoint()
            throws IOException, NoSuchFieldException, IllegalAccessException
    {
        try (Fluency fluency = new FluencyBuilderForTreasureData().build(APIKEY, "https://custom.endpoint.org")) {
            assertBuffer((DefaultBuffer) fluency.getBuffer());
            assertFlusher(fluency.getFlusher());
            assertDefaultFluentdSender(
                    (TreasureDataSender) fluency.getFlusher().getIngester().getSender(),
                    "custom.endpoint.org", true, APIKEY);
        }
    }

    @Test
    public void buildWithCustomHttpsEndpointWithoutScheme()
            throws IOException, NoSuchFieldException, IllegalAccessException
    {
        try (Fluency fluency = new FluencyBuilderForTreasureData().build(APIKEY, "custom.endpoint.org")) {
            assertBuffer((DefaultBuffer) fluency.getBuffer());
            assertFlusher(fluency.getFlusher());
            assertDefaultFluentdSender(
                    (TreasureDataSender) fluency.getFlusher().getIngester().getSender(),
                    "custom.endpoint.org", true, APIKEY);
        }
    }

    @Test
    public void buildWithCustomHttpEndpoint()
            throws IOException, NoSuchFieldException, IllegalAccessException
    {
        try (Fluency fluency = new FluencyBuilderForTreasureData().build(APIKEY, "http://custom.endpoint.org")) {
            assertBuffer((DefaultBuffer) fluency.getBuffer());
            assertFlusher(fluency.getFlusher());
            assertDefaultFluentdSender(
                    (TreasureDataSender) fluency.getFlusher().getIngester().getSender(),
                    "custom.endpoint.org", false, APIKEY);
        }
    }

    @Test
    public void buildWithAllCustomConfig()
            throws IOException
    {
        String tmpdir = System.getProperty("java.io.tmpdir");
        assertThat(tmpdir, is(notNullValue()));

        FluencyBuilderForTreasureData builder = new FluencyBuilderForTreasureData();
        builder.setFlushAttemptIntervalMillis(200);
        builder.setMaxBufferSize(Long.MAX_VALUE);
        builder.setBufferChunkInitialSize(7 * 1024 * 1024);
        builder.setBufferChunkRetentionSize(13 * 1024 * 1024);
        builder.setBufferChunkRetentionTimeMillis(19 * 1000);
        builder.setJvmHeapBufferMode(true);
        builder.setWaitUntilBufferFlushed(42);
        builder.setWaitUntilFlusherTerminated(24);
        builder.setFileBackupDir(tmpdir);
        builder.setSenderRetryIntervalMillis(1234);
        builder.setSenderMaxRetryIntervalMillis(345678);
        builder.setSenderRetryFactor(3.14f);
        builder.setSenderRetryMax(17);
        builder.setSenderWorkBufSize(123456);

        try (Fluency fluency = builder.build(APIKEY)) {
            assertThat(fluency.getBuffer(), instanceOf(DefaultBuffer.class));
            DefaultBuffer buffer = (DefaultBuffer) fluency.getBuffer();
            assertThat(buffer.getMaxBufferSize(), is(Long.MAX_VALUE));
            assertThat(buffer.getFileBackupDir(), is(tmpdir));
            assertThat(buffer.bufferFormatType(), is("packed_forward"));
            assertThat(buffer.getChunkRetentionTimeMillis(), is(19 * 1000));
            assertThat(buffer.getChunkExpandRatio(), is(2f));
            assertThat(buffer.getChunkInitialSize(), is(7 * 1024 * 1024));
            assertThat(buffer.getChunkRetentionSize(), is(13 * 1024 * 1024));
            assertThat(buffer.getJvmHeapBufferMode(), is(true));

            Flusher flusher = fluency.getFlusher();
            assertThat(flusher.isTerminated(), is(false));
            assertThat(flusher.getFlushAttemptIntervalMillis(), is(200));
            assertThat(flusher.getWaitUntilBufferFlushed(), is(42));
            assertThat(flusher.getWaitUntilTerminated(), is(24));

            assertThat(flusher.getIngester().getSender(), instanceOf(TreasureDataSender.class));
            TreasureDataSender sender = (TreasureDataSender) flusher.getIngester().getSender();
            assertThat(sender.getRetryInternalMs(), is(1234));
            assertThat(sender.getMaxRetryInternalMs(), is(345678));
            assertThat(sender.getRetryFactor(), is(3.14f));
            assertThat(sender.getRetryMax(), is(17));
            assertThat(sender.getWorkBufSize(), is(123456));
        }
    }
}
