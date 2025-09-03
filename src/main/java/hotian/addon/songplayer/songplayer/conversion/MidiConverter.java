package hotian.addon.songplayer.songplayer.conversion;

import hotian.addon.songplayer.songplayer.song.DownloadUtils;
import hotian.addon.songplayer.songplayer.song.Instrument;
import hotian.addon.songplayer.songplayer.song.Note;
import hotian.addon.songplayer.songplayer.song.Song;

import javax.sound.midi.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class MidiConverter {
	public static final int SET_INSTRUMENT = 0xC0;
	public static final int SET_TEMPO = 0x51;
	public static final int NOTE_ON = 0x90;
    public static final int NOTE_OFF = 0x80;

	public static Song getSongFromUrl(URL url) throws IOException, InvalidMidiDataException, URISyntaxException, NoSuchAlgorithmException, KeyManagementException {
		Sequence sequence = MidiSystem.getSequence(DownloadUtils.DownloadToInputStream(url, 5*1024*1024));
		return getSong(sequence, Paths.get(url.toURI().getPath()).getFileName().toString());
	}

	public static Song getSongFromFile(Path file) throws InvalidMidiDataException, IOException {
		Sequence sequence = MidiSystem.getSequence(file.toFile());
		return getSong(sequence, file.getFileName().toString());
	}

	public static Song getSongFromBytes(byte[] bytes, String name) throws InvalidMidiDataException, IOException {
		Sequence sequence = MidiSystem.getSequence(new ByteArrayInputStream(bytes));
		return getSong(sequence, name);
	}
    
	public static Song getSong(Sequence sequence, String name) {
		Song song  = new Song(name);
		
		long tpq = sequence.getResolution();
		
		ArrayList<MidiEvent> tempoEvents = new ArrayList<>();
		for (Track track : sequence.getTracks()) {
			for (int i = 0; i < track.size(); i++) {
				MidiEvent event = track.get(i);
				MidiMessage message = event.getMessage();
				if (message instanceof MetaMessage) {
					MetaMessage mm = (MetaMessage) message;
					if (mm.getType() == SET_TEMPO) {
						tempoEvents.add(event);
					}
				}
			}
		}
		
		Collections.sort(tempoEvents, (a, b) -> Long.compare(a.getTick(), b.getTick()));
		
		for (Track track : sequence.getTracks()) {

			long microTime = 0;
			int[] instrumentIds = new int[16];
			int mpq = 500000;
			int tempoEventIdx = 0;
			long prevTick = 0;
			
			for (int i = 0; i < track.size(); i++) {
				MidiEvent event = track.get(i);
				MidiMessage message = event.getMessage();
				
				while (tempoEventIdx < tempoEvents.size() && event.getTick() > tempoEvents.get(tempoEventIdx).getTick()) {
					long deltaTick = tempoEvents.get(tempoEventIdx).getTick() - prevTick;
					prevTick = tempoEvents.get(tempoEventIdx).getTick();
					microTime += (mpq/tpq) * deltaTick;
					
					MetaMessage mm = (MetaMessage) tempoEvents.get(tempoEventIdx).getMessage();
					byte[] data = mm.getData();
					int new_mpq = (data[2]&0xFF) | ((data[1]&0xFF)<<8) | ((data[0]&0xFF)<<16);
					if (new_mpq != 0) mpq = new_mpq;
					tempoEventIdx++;
				}
				
				if (message instanceof ShortMessage) {
					ShortMessage sm = (ShortMessage) message;
					if (sm.getCommand() == SET_INSTRUMENT) {
						instrumentIds[sm.getChannel()] = sm.getData1();
					}
					else if (sm.getCommand() == NOTE_ON) {
						int pitch = sm.getData1();
						int velocity = sm.getData2();
						if (velocity == 0) continue; // Just ignore notes with velocity 0
						velocity = (velocity * 100) / 127; // Midi velocity goes from 0-127
						long deltaTick = event.getTick() - prevTick;
						prevTick = event.getTick();
						microTime += (mpq/tpq) * deltaTick;

						Note note;
						if (sm.getChannel() == 9) {
							note = getMidiPercussionNote(pitch, velocity, microTime);
						}
						else {
							note = getMidiInstrumentNote(instrumentIds[sm.getChannel()], pitch, velocity, microTime);
						}
						if (note != null) {
							song.add(note);
						}

						long time = microTime / 1000L;
						if (time > song.length) {
							song.length = time;
						}
					}
					else if (sm.getCommand() == NOTE_OFF) {
						long deltaTick = event.getTick() - prevTick;
						prevTick = event.getTick();
						microTime += (mpq/tpq) * deltaTick;
						long time = microTime / 1000L;
						if (time > song.length) {
							song.length = time;
						}
					}
				}
			}
		}

		song.sort();

		// Shift to beginning if delay is too long
		if (!song.notes.isEmpty()) {
			long shift = song.notes.get(0).time - 1000;
			if (song.notes.get(0).time > 1000) {
				for (Note note : song.notes) {
					note.time -= shift;
				}
			}
			song.length -= shift;
		}
		
		return song;
	}

	public static Note getMidiInstrumentNote(int midiInstrument, int midiPitch, int velocity, long microTime) {
		hotian.addon.songplayer.songplayer.song.Instrument instrument = null;
		hotian.addon.songplayer.songplayer.song.Instrument[] instrumentList = instrumentMap.get(midiInstrument);
		if (instrumentList != null) {
			for (hotian.addon.songplayer.songplayer.song.Instrument candidateInstrument : instrumentList) {
				if (midiPitch >= candidateInstrument.offset && midiPitch <= candidateInstrument.offset+24) {
					instrument = candidateInstrument;
					break;
				}
			}
		}

		if (instrument == null) {
			return null;
		}

		int pitch = midiPitch-instrument.offset;
		int noteId = pitch + instrument.instrumentId*25;
		long time = microTime / 1000L;

		return new Note(noteId, time, velocity);
	}

	private static Note getMidiPercussionNote(int midiPitch, int velocity, long microTime) {
		if (percussionMap.containsKey(midiPitch)) {
			int noteId = percussionMap.get(midiPitch);
			long time = microTime / 1000L;

			return new Note(noteId, time, velocity);
		}
		return null;
	}

	public static HashMap<Integer, hotian.addon.songplayer.songplayer.song.Instrument[]> instrumentMap = new HashMap<>();
	static {
		// Piano (HARP BASS BELL)
		instrumentMap.put(0, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // Acoustic Grand Piano
		instrumentMap.put(1, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // Bright Acoustic Piano
		instrumentMap.put(2, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.BIT, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // Electric Grand Piano
		instrumentMap.put(3, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // Honky-tonk Piano
		instrumentMap.put(4, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.BIT, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // Electric Piano 1
		instrumentMap.put(5, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.BIT, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // Electric Piano 2
		instrumentMap.put(6, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // Harpsichord
		instrumentMap.put(7, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // Clavinet

		// Chromatic Percussion (IRON_XYLOPHONE XYLOPHONE BASS)
		instrumentMap.put(8, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.XYLOPHONE}); // Celesta
		instrumentMap.put(9, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.XYLOPHONE}); // Glockenspiel
		instrumentMap.put(10, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.XYLOPHONE}); // Music Box
		instrumentMap.put(11, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.XYLOPHONE}); // Vibraphone
		instrumentMap.put(12, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.XYLOPHONE}); // Marimba
		instrumentMap.put(13, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.XYLOPHONE}); // Xylophone
		instrumentMap.put(14, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.XYLOPHONE}); // Tubular Bells
		instrumentMap.put(15, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.XYLOPHONE}); // Dulcimer

		// Organ (BIT DIDGERIDOO BELL)
		instrumentMap.put(16, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.BIT, hotian.addon.songplayer.songplayer.song.Instrument.XYLOPHONE}); // Drawbar Organ
		instrumentMap.put(17, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.BIT, hotian.addon.songplayer.songplayer.song.Instrument.XYLOPHONE}); // Percussive Organ
		instrumentMap.put(18, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.BIT, hotian.addon.songplayer.songplayer.song.Instrument.XYLOPHONE}); // Rock Organ
		instrumentMap.put(19, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.BIT, hotian.addon.songplayer.songplayer.song.Instrument.XYLOPHONE}); // Church Organ
		instrumentMap.put(20, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.BIT, hotian.addon.songplayer.songplayer.song.Instrument.XYLOPHONE}); // Reed Organ
		instrumentMap.put(21, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.BIT, hotian.addon.songplayer.songplayer.song.Instrument.XYLOPHONE}); // Accordian
		instrumentMap.put(22, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.BIT, hotian.addon.songplayer.songplayer.song.Instrument.XYLOPHONE}); // Harmonica
		instrumentMap.put(23, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.BIT, hotian.addon.songplayer.songplayer.song.Instrument.XYLOPHONE}); // Tango Accordian

		// Guitar (BIT DIDGERIDOO BELL)
		instrumentMap.put(24, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.GUITAR, hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // Acoustic Guitar (nylon)
		instrumentMap.put(25, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.GUITAR, hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // Acoustic Guitar (steel)
		instrumentMap.put(26, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.GUITAR, hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // Electric Guitar (jazz)
		instrumentMap.put(27, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.GUITAR, hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // Electric Guitar (clean)
		instrumentMap.put(28, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.GUITAR, hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // Electric Guitar (muted)
		instrumentMap.put(29, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.BIT, hotian.addon.songplayer.songplayer.song.Instrument.XYLOPHONE}); // Overdriven Guitar
		instrumentMap.put(30, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.BIT, hotian.addon.songplayer.songplayer.song.Instrument.XYLOPHONE}); // Distortion Guitar
		instrumentMap.put(31, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.GUITAR, hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // Guitar Harmonics

		// Bass
		instrumentMap.put(32, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // Acoustic Bass
		instrumentMap.put(33, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // Electric Bass (finger)
		instrumentMap.put(34, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // Electric Bass (pick)
		instrumentMap.put(35, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // Fretless Bass
		instrumentMap.put(36, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.BIT, hotian.addon.songplayer.songplayer.song.Instrument.XYLOPHONE}); // Slap Bass 1
		instrumentMap.put(37, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.BIT, hotian.addon.songplayer.songplayer.song.Instrument.XYLOPHONE}); // Slap Bass 2
		instrumentMap.put(38, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.BIT, hotian.addon.songplayer.songplayer.song.Instrument.XYLOPHONE}); // Synth Bass 1
		instrumentMap.put(39, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.BIT, hotian.addon.songplayer.songplayer.song.Instrument.XYLOPHONE}); // Synth Bass 2

		// Strings
		instrumentMap.put(40, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.FLUTE, hotian.addon.songplayer.songplayer.song.Instrument.GUITAR, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // Violin
		instrumentMap.put(41, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.FLUTE, hotian.addon.songplayer.songplayer.song.Instrument.GUITAR, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // Viola
		instrumentMap.put(42, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.FLUTE, hotian.addon.songplayer.songplayer.song.Instrument.GUITAR, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // Cello
		instrumentMap.put(43, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.FLUTE, hotian.addon.songplayer.songplayer.song.Instrument.GUITAR, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // Contrabass
		instrumentMap.put(44, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.BIT, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // Tremolo Strings
		instrumentMap.put(45, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // Pizzicato Strings
		instrumentMap.put(46, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.CHIME}); // Orchestral Harp
		instrumentMap.put(47, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // Timpani

		// Ensenble
		instrumentMap.put(48, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // String Ensemble 1
		instrumentMap.put(49, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // String Ensemble 2
		instrumentMap.put(50, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // Synth Strings 1
		instrumentMap.put(51, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // Synth Strings 2
		instrumentMap.put(52, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // Choir Aahs
		instrumentMap.put(53, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // Voice Oohs
		instrumentMap.put(54, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // Synth Choir
		instrumentMap.put(55, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL}); // Orchestra Hit

		// Brass
		instrumentMap.put(56, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.BIT, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(57, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.BIT, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(58, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.BIT, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(59, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.BIT, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(60, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.BIT, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(61, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.BIT, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(62, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.BIT, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(63, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.BIT, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.BELL});

		// Reed
		instrumentMap.put(64, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.FLUTE, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(65, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.FLUTE, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(66, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.FLUTE, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(67, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.FLUTE, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(68, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.FLUTE, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(69, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.FLUTE, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(70, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.FLUTE, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(71, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.FLUTE, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BELL});

		// Pipe
		instrumentMap.put(72, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.FLUTE, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(73, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.FLUTE, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(74, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.FLUTE, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(75, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.FLUTE, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(76, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.FLUTE, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(77, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.FLUTE, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(78, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.FLUTE, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(79, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.FLUTE, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BELL});

		// Synth Lead
		instrumentMap.put(80, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(81, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(82, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(83, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(84, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(85, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(86, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(87, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL});

		// Synth Pad
		instrumentMap.put(88, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(89, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(90, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(91, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(92, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(93, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(94, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(95, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL});

		// Synth Effects
//		instrumentMap.put(96, new Instrument[]{});
//		instrumentMap.put(97, new Instrument[]{});
		instrumentMap.put(98, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.BIT, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(99, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(100, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(101, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(102, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(103, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL});

		// Ethnic
		instrumentMap.put(104, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.BANJO, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(105, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.BANJO, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(106, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.BANJO, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(107, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.BANJO, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(108, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.BANJO, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(109, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(110, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.BELL});
		instrumentMap.put(111, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.HARP, hotian.addon.songplayer.songplayer.song.Instrument.DIDGERIDOO, hotian.addon.songplayer.songplayer.song.Instrument.BELL});

		// Percussive
		instrumentMap.put(112, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.XYLOPHONE});
		instrumentMap.put(113, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.XYLOPHONE});
		instrumentMap.put(114, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.XYLOPHONE});
		instrumentMap.put(115, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.XYLOPHONE});
		instrumentMap.put(116, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.XYLOPHONE});
		instrumentMap.put(117, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.XYLOPHONE});
		instrumentMap.put(118, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.XYLOPHONE});
		instrumentMap.put(119, new hotian.addon.songplayer.songplayer.song.Instrument[]{hotian.addon.songplayer.songplayer.song.Instrument.IRON_XYLOPHONE, hotian.addon.songplayer.songplayer.song.Instrument.BASS, hotian.addon.songplayer.songplayer.song.Instrument.XYLOPHONE});
	}

	public static HashMap<Integer, Integer> percussionMap = new HashMap<>();
	static {
		percussionMap.put(35, 10 + 25* hotian.addon.songplayer.songplayer.song.Instrument.BASEDRUM.instrumentId);
		percussionMap.put(36, 6  + 25* hotian.addon.songplayer.songplayer.song.Instrument.BASEDRUM.instrumentId);
		percussionMap.put(37, 6  + 25* hotian.addon.songplayer.songplayer.song.Instrument.HAT.instrumentId);
		percussionMap.put(38, 8  + 25* hotian.addon.songplayer.songplayer.song.Instrument.SNARE.instrumentId);
		percussionMap.put(39, 6  + 25* hotian.addon.songplayer.songplayer.song.Instrument.HAT.instrumentId);
		percussionMap.put(40, 4  + 25* hotian.addon.songplayer.songplayer.song.Instrument.SNARE.instrumentId);
		percussionMap.put(41, 6  + 25* hotian.addon.songplayer.songplayer.song.Instrument.BASEDRUM.instrumentId);
		percussionMap.put(42, 22 + 25* hotian.addon.songplayer.songplayer.song.Instrument.SNARE.instrumentId);
		percussionMap.put(43, 13 + 25* hotian.addon.songplayer.songplayer.song.Instrument.BASEDRUM.instrumentId);
		percussionMap.put(44, 22 + 25* hotian.addon.songplayer.songplayer.song.Instrument.SNARE.instrumentId);
		percussionMap.put(45, 15 + 25* hotian.addon.songplayer.songplayer.song.Instrument.BASEDRUM.instrumentId);
		percussionMap.put(46, 18 + 25* hotian.addon.songplayer.songplayer.song.Instrument.SNARE.instrumentId);
		percussionMap.put(47, 20 + 25* hotian.addon.songplayer.songplayer.song.Instrument.BASEDRUM.instrumentId);
		percussionMap.put(48, 23 + 25* hotian.addon.songplayer.songplayer.song.Instrument.BASEDRUM.instrumentId);
		percussionMap.put(49, 17 + 25* hotian.addon.songplayer.songplayer.song.Instrument.SNARE.instrumentId);
		percussionMap.put(50, 23 + 25* hotian.addon.songplayer.songplayer.song.Instrument.BASEDRUM.instrumentId);
		percussionMap.put(51, 24 + 25* hotian.addon.songplayer.songplayer.song.Instrument.SNARE.instrumentId);
		percussionMap.put(52, 8  + 25* hotian.addon.songplayer.songplayer.song.Instrument.SNARE.instrumentId);
		percussionMap.put(53, 13 + 25* hotian.addon.songplayer.songplayer.song.Instrument.SNARE.instrumentId);
		percussionMap.put(54, 18 + 25* hotian.addon.songplayer.songplayer.song.Instrument.HAT.instrumentId);
		percussionMap.put(55, 18 + 25* hotian.addon.songplayer.songplayer.song.Instrument.SNARE.instrumentId);
		percussionMap.put(56, 1  + 25* hotian.addon.songplayer.songplayer.song.Instrument.HAT.instrumentId);
		percussionMap.put(57, 13 + 25* hotian.addon.songplayer.songplayer.song.Instrument.SNARE.instrumentId);
		percussionMap.put(58, 2  + 25* hotian.addon.songplayer.songplayer.song.Instrument.HAT.instrumentId);
		percussionMap.put(59, 13 + 25* hotian.addon.songplayer.songplayer.song.Instrument.SNARE.instrumentId);
		percussionMap.put(60, 9  + 25* hotian.addon.songplayer.songplayer.song.Instrument.HAT.instrumentId);
		percussionMap.put(61, 2  + 25* hotian.addon.songplayer.songplayer.song.Instrument.HAT.instrumentId);
		percussionMap.put(62, 8  + 25* hotian.addon.songplayer.songplayer.song.Instrument.HAT.instrumentId);
		percussionMap.put(63, 22 + 25* hotian.addon.songplayer.songplayer.song.Instrument.BASEDRUM.instrumentId);
		percussionMap.put(64, 15 + 25* hotian.addon.songplayer.songplayer.song.Instrument.BASEDRUM.instrumentId);
		percussionMap.put(65, 13 + 25* hotian.addon.songplayer.songplayer.song.Instrument.SNARE.instrumentId);
		percussionMap.put(66, 8  + 25* hotian.addon.songplayer.songplayer.song.Instrument.SNARE.instrumentId);
		percussionMap.put(67, 8  + 25* hotian.addon.songplayer.songplayer.song.Instrument.HAT.instrumentId);
		percussionMap.put(68, 3  + 25* hotian.addon.songplayer.songplayer.song.Instrument.HAT.instrumentId);
		percussionMap.put(69, 20 + 25* hotian.addon.songplayer.songplayer.song.Instrument.HAT.instrumentId);
		percussionMap.put(70, 23 + 25* hotian.addon.songplayer.songplayer.song.Instrument.HAT.instrumentId);
		percussionMap.put(71, 24 + 25* hotian.addon.songplayer.songplayer.song.Instrument.HAT.instrumentId);
		percussionMap.put(72, 24 + 25* hotian.addon.songplayer.songplayer.song.Instrument.HAT.instrumentId);
		percussionMap.put(73, 17 + 25* hotian.addon.songplayer.songplayer.song.Instrument.HAT.instrumentId);
		percussionMap.put(74, 11 + 25* hotian.addon.songplayer.songplayer.song.Instrument.HAT.instrumentId);
		percussionMap.put(75, 18 + 25* hotian.addon.songplayer.songplayer.song.Instrument.HAT.instrumentId);
		percussionMap.put(76, 9  + 25* hotian.addon.songplayer.songplayer.song.Instrument.HAT.instrumentId);
		percussionMap.put(77, 5  + 25* hotian.addon.songplayer.songplayer.song.Instrument.HAT.instrumentId);
		percussionMap.put(78, 22 + 25* hotian.addon.songplayer.songplayer.song.Instrument.HAT.instrumentId);
		percussionMap.put(79, 19 + 25* hotian.addon.songplayer.songplayer.song.Instrument.SNARE.instrumentId);
		percussionMap.put(80, 17 + 25* hotian.addon.songplayer.songplayer.song.Instrument.HAT.instrumentId);
		percussionMap.put(81, 22 + 25* hotian.addon.songplayer.songplayer.song.Instrument.HAT.instrumentId);
		percussionMap.put(82, 22 + 25* hotian.addon.songplayer.songplayer.song.Instrument.SNARE.instrumentId);
		percussionMap.put(83, 24 + 25* hotian.addon.songplayer.songplayer.song.Instrument.CHIME.instrumentId);
		percussionMap.put(84, 24 + 25* hotian.addon.songplayer.songplayer.song.Instrument.CHIME.instrumentId);
		percussionMap.put(85, 21 + 25* hotian.addon.songplayer.songplayer.song.Instrument.HAT.instrumentId);
		percussionMap.put(86, 14 + 25* hotian.addon.songplayer.songplayer.song.Instrument.BASEDRUM.instrumentId);
		percussionMap.put(87, 7  + 25* Instrument.BASEDRUM.instrumentId);
	}
}
