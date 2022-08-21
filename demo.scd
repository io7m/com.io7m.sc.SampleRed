(
Server.local.stop;
Quarks.clear;
Quarks.addFolder("~/git/com.github/io7m/quarks");
Quarks.install("com.io7m.sc.TypeChecking");
Quarks.install("com.io7m.sc.Assert");
Quarks.install("com.io7m.sc.SampleRed");
Exception.debug = false;
Server.local.boot;
)

(
var collection =
  IO7MSRSampleCollection.openFromPath(Server.local, PathName.new("example.txt"));
var sampler =
  IO7MSRSampler.new(collection);

NoteOnResponder.removeAll;
NoteOffResponder.removeAll;
CCResponder.removeAll;

NoteOnResponder(function: {|src, chan, note, vel|
	sampler.keyOn(note);
});

NoteOffResponder(function: {|src, chan, note, vel|
	sampler.keyOff(note);
});
)
