/*
 * Copyright © 2022 Mark Raynsford <code@io7m.com> https://www.io7m.com
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR
 * IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

IO7MSRStrictObject
{
  doesNotUnderstand
  {
    | selector...args | DoesNotUnderstandError.new (this, selector, args).throw;
  }
}

IO7MSRSampler : IO7MSRStrictObject
{
  var <samples;
  var <>voices;

  *new
  {
    arg samples;

    IO7MTypeChecking.check(IO7MSRSampleCollection, samples);

    ^super.newCopyArgs(samples).init();
  }

  init
  {
    this.voices = Dictionary.new(samples.size);

    SynthDef(\io7m_samplerRed, {
      arg buffer;

      var snd = PlayBuf.ar(
        2,
        buffer,
        BufRateScale.kr(buffer),
        doneAction: Done.freeSelf
      );

      Out.ar(0, snd);
    }).add;
  }

  keyOn
  {
    arg keyNumber;
    var sample;

    IO7MTypeChecking.check(Integer, keyNumber);

    sample = this.samples.samples[keyNumber];
    if (sample != nil, {
      var synth;
      IO7MTypeChecking.check(IO7MSRSample, sample);
      synth = Synth.new(\io7m_samplerRed);
      synth.set(\buffer, sample.buffer);
      NodeWatcher.register(synth, assumePlaying: true);
      this.voices.put(keyNumber, synth);
    });
  }

  keyOff
  {
    arg keyNumber;
    var voice;

    IO7MTypeChecking.check(Integer, keyNumber);

    voice = this.voices[keyNumber];
    if (voice != nil, {
      this.voices.removeAt(keyNumber);
      if (voice.isPlaying, {
        voice.free;
      });
    });
  }
}

IO7MSRSampleRecord : IO7MSRStrictObject
{
  var <keyNumber;
  var <file;

  // Integer -> PathName -> IO7MSRSampleRecord
  *new
  {
    arg keyNumber, file;

    IO7MTypeChecking.check(Integer, keyNumber);
    IO7MTypeChecking.check(PathName, file);

    IO7MAssertions.checkAssertion(
      file,
      file.isAbsolutePath,
      "Path must be absolute."
    );

    ^super.newCopyArgs(keyNumber, file);
  }

  // Array -> String -> IO7MSRSampleRecord
  *fromRecord
  {
    arg record, baseDirectory;

    var keyNumber;
    var file;

    IO7MTypeChecking.check(Array, record);
    IO7MTypeChecking.check(String, baseDirectory);

    IO7MAssertions.checkPrecondition(
      record,
      record.size == 2,
      "Input records must contain a pair of values"
    );

    IO7MAssertions.checkPrecondition(
      baseDirectory,
      baseDirectory != "",
      "Base directory must be non-empty"
    );

    IO7MTypeChecking.check(String, record[0]);
    IO7MTypeChecking.check(String, record[1]);

    keyNumber = record[0].asInteger;
    file      = PathName.new(baseDirectory) +/+ PathName(record[1]);

    ^IO7MSRSampleRecord.new(keyNumber, file);
  }
}

IO7MSRSampleRecordCollection : IO7MSRStrictObject
{
  var <sampleRecords;

  // Dictionary -> IO7MSRSampleRecordCollection
  *new
  {
    arg sampleRecords;

    IO7MTypeChecking.check(Dictionary, sampleRecords);

    ^super.newCopyArgs(sampleRecords);
  }

  // PathName -> IO7MSRSampleRecordCollection
  *openFromPath
  {
    arg path;

    var records;
    var samples;
    var fullPath;

    IO7MTypeChecking.check(PathName, path);

    fullPath = PathName.new(path.asAbsolutePath);

    records = SemiColonFileReader.read(
      path: fullPath.absolutePath,
      skipEmptyLines: true,
      skipBlanks: true
    );

    samples = Dictionary.new(n: records.size);
    records.do({
      arg item, index;
      var record;

      record = IO7MSRSampleRecord.fromRecord(item, fullPath.pathOnly);
      IO7MTypeChecking.check(IO7MSRSampleRecord, record);
      samples.put(record.keyNumber, record);
    });

    ^IO7MSRSampleRecordCollection.new(samples);
  }
}

IO7MSRSample : IO7MSRStrictObject
{
  var <keyNumber;
  var <buffer;

  // Integer -> Buffer -> IO7MSRSample
  *new
  {
    arg keyNumber, buffer;

    IO7MTypeChecking.check(Integer, keyNumber);
    IO7MTypeChecking.check(Buffer, buffer);

    ^super.newCopyArgs(keyNumber, buffer);
  }

  // Server -> IO7MSRSampleRecord -> IO7MSRSample
  *fromRecord
  {
    arg server, record;

    var buffer;

    IO7MTypeChecking.check(Server, server);
    IO7MTypeChecking.check(IO7MSRSampleRecord, record);

    IO7MAssertions.checkAssertion(
      record.file.absolutePath,
      File.exists(record.file.absolutePath),
      "File must exist."
    );

    buffer = Buffer.read(server, record.file.absolutePath);

    ^IO7MSRSample.new(record.keyNumber, buffer);
  }
}

IO7MSRSampleCollection : IO7MSRStrictObject
{
  var <samples;

  // Dictionary -> IO7MSRSampleCollection
  *new
  {
    arg samples;

    IO7MTypeChecking.check(Dictionary, samples);

    ^super.newCopyArgs(samples);
  }

  // Server -> IO7MSRSampleRecordCollection -> IO7MSRSampleCollection
  *fromRecordCollection
  {
    arg server, records;

    IO7MTypeChecking.check(Server, server);
    IO7MTypeChecking.check(IO7MSRSampleRecordCollection, records);

    ^IO7MSRSampleCollection.new(records.sampleRecords.collect({
      arg item, index;
      IO7MSRSample.fromRecord(server, item);
    }));
  }

  // Server -> PathName -> IO7MSRSampleCollection
  *openFromPath
  {
    arg server, path;

    IO7MTypeChecking.check(Server, server);
    IO7MTypeChecking.check(PathName, path);

    ^IO7MSRSampleCollection.fromRecordCollection(
      server,
      IO7MSRSampleRecordCollection.openFromPath(path)
    );
  }
}
