/*******************************************************************************
 * Copyright (c) 2013, Fabrizio Falchi (NeMIS Lab., ISTI-CNR, Italy)
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met: 
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer. 
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution. 
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package it.cnr.isti.vir.file;

import gnu.trove.list.array.TLongArrayList;
import it.cnr.isti.vir.features.AbstractFeature;
import it.cnr.isti.vir.features.AbstractFeaturesCollector;
import it.cnr.isti.vir.features.FeatureClassCollector;
import it.cnr.isti.vir.features.FeaturesCollectorArr;
import it.cnr.isti.vir.features.FeaturesCollectors;
import it.cnr.isti.vir.features.localfeatures.ALocalFeature;
import it.cnr.isti.vir.features.localfeatures.ALocalFeaturesGroup;
import it.cnr.isti.vir.global.Log;
import it.cnr.isti.vir.global.ParallelOptions;
import it.cnr.isti.vir.id.AbstractID;
import it.cnr.isti.vir.id.IDClasses;
import it.cnr.isti.vir.id.IDString;
import it.cnr.isti.vir.id.IHasID;
import it.cnr.isti.vir.similarity.ISimilarity;
import it.cnr.isti.vir.similarity.index.FeaturesCollectorsArchiveSearch;
import it.cnr.isti.vir.similarity.metric.IMetric;
import it.cnr.isti.vir.similarity.pqueues.SimPQueueDMax;
import it.cnr.isti.vir.similarity.results.SimilarityResults;
import it.cnr.isti.vir.util.RandomOperations;
import it.cnr.isti.vir.util.TimeManager;
import it.cnr.isti.vir.util.WorkingPath;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

public class FeaturesCollectorsArchive implements Iterable<AbstractFeaturesCollector> {

	public static final long fileID = 0x5a25287d3aL;

	public static final int version = 3;

	private TLongArrayList offset;
	private ArrayList<AbstractID> ids;
	// private final HashMap<IID,Long> idOffsetMap;
	private HashMap<AbstractID, Integer> idPosMap;
	
	private final RandomAccessFile rndFile;
	
	private RandomAccessFile rndFile_Offset;
	
	
	private final File f;
	
	private File offsetFile;
	private File idFile;
//	private final FeatureClassCollector featuresClasses;
	private final Constructor fcClassConstructor;
	private final Constructor fcClassConstructor_NIO;

	private final Class<? extends AbstractID> idClass;

	private boolean changed = false;
	private int lastSaveSize = -1;
	
	private final Class<? extends AbstractFeaturesCollector> fcClass;

	private boolean closed = false;
	
//	public FeatureClassCollector getFeaturesClasses() {
//		return featuresClasses;
//	}

	private int size; 

	public Class<? extends AbstractFeaturesCollector> getFcClass() {
		return fcClass;
	}

	public int size() {
		return size;
//		if ( offset != null ) offset.size();
//		
//		try {
//			synchronized( rndFile_Offset ) {
//				return (int) (rndFile_Offset.length()/Long.BYTES);
//			}
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//			return -1;
//		}
	}

	public Class<? extends AbstractID> getIDClass() {
		return idClass;
	}

	public final AbstractID getID(int i) {
		return ids.get(i);
	}
	

	public FeaturesCollectorsArchive getSameType(File file) throws Exception {
		return new FeaturesCollectorsArchive(file, idClass, fcClass);
	}
	
	
	/**
	 * This static method create a FeaturesCollectorsArchive that uses
	 * IDString and FeaturesCollectorArr for storing features.
	 * 
	 * @param The file in which the archive will be created
	 * @return The created archive
	 * @throws Exception
	 */
	public static  FeaturesCollectorsArchive create(File file ) throws Exception {
		return new FeaturesCollectorsArchive(file, IDString.class, FeaturesCollectorArr.class );
	}
	
	/**
	 * This static method create a FeaturesCollectorsArchive that uses
	 * the given ID class and FeaturesCollectorArr for storing features.
	 * @param file
	 * @param idclass
	 * @return
	 * @throws Exception
	 */
	public static  FeaturesCollectorsArchive create(File file, Class<? extends AbstractID> idclass ) throws Exception {
		return new FeaturesCollectorsArchive(file, idclass, FeaturesCollectorArr.class );
	}

	public static  FeaturesCollectorsArchive create(File file, Class<? extends AbstractID> idclass, Class<? extends AbstractFeaturesCollector> fcClass ) throws Exception {
		return new FeaturesCollectorsArchive(file, idclass, fcClass );
	}

	public FeaturesCollectorsArchive(String fileName ) throws SecurityException, NoSuchMethodException, IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException, IOException {
		this(WorkingPath.getFile(fileName));
	}
	
	public FeaturesCollectorsArchive(String fileName, boolean readIDs ) throws SecurityException, NoSuchMethodException, IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException, IOException {
		this(WorkingPath.getFile(fileName), readIDs);
	}

	/**
	 * Constructs an FeaturesCollectorsArchive loading an existing archive.
	 * @param file The file containing the archive
	 */
	public FeaturesCollectorsArchive(File file ) throws IOException,
			SecurityException, NoSuchMethodException, IllegalArgumentException,
			InstantiationException, IllegalAccessException,
			InvocationTargetException {
		this(file, true);
	}

	
	
	/**
	 * @param file	File for the new archive
	 * @param featuresClasses Features
	 * @param idClass Class for the IDs
	 * @param fcClass Class for the Features Collection
	 * @throws Exception
	 */
	public FeaturesCollectorsArchive(
			File file,
			//FeatureClassCollector featuresClasses,
			Class<? extends AbstractID> idClass,
			Class<? extends AbstractFeaturesCollector> fcClass)
					throws Exception {
		
		if ( file.exists() && !file.delete() )
			throw new Exception("Unable to delete file " + file.getAbsolutePath());
		rndFile = new RandomAccessFile(file, "rw");
		rndFile.setLength(0);
		//rndFile_Offset = new RandomAccessFile( this.getOffsetFileName(file), "rw");
		//this.offset = null;
		
		this.offset = new TLongArrayList();
		
		this.f = file;
		//this.featuresClasses = featuresClasses;
		this.idClass = idClass;
		this.fcClass = fcClass;
		this.fcClassConstructor = getFCConstructor(fcClass);
		this.fcClassConstructor_NIO = getFCConstructor_NIO(fcClass);
		
		
		this.ids = new ArrayList();
		this.idPosMap = new HashMap();
		idFile = new File(getIDFileName(file));
		offsetFile = new File(getOffsetFileName(file));
		rndFile_Offset = new RandomAccessFile(offsetFile, "rw");
		rndFile_Offset.setLength(0);
		
		size = 0;
		
		writeIntro(rndFile, idClass, fcClass);
	}

	public static final void writeIntro(DataOutput out,
			Class<? extends AbstractID> idClass, Class<? extends AbstractFeaturesCollector> fcClass)
			throws Exception {
		out.writeLong(fileID);
		out.writeInt(version);
		FeaturesCollectors.writeClass(fcClass, out);
		//featuresClasses.writeData(out);
		IDClasses.writeClass_Int(idClass, out);
	}

	protected static final Constructor<? extends AbstractFeaturesCollector> getFCConstructor(Class<? extends AbstractFeaturesCollector> c)
			throws SecurityException, NoSuchMethodException {
		if (c == null)
			return null;
		return c.getConstructor(DataInput.class);
	}

	protected static final Constructor<? extends AbstractFeaturesCollector> getFCConstructor_NIO(Class<? extends AbstractFeaturesCollector> c)
			throws SecurityException, NoSuchMethodException {
		if (c == null)
			return null;
		return c.getConstructor(ByteBuffer.class);
	}
	
	public synchronized void add(AbstractFeature f, AbstractID id ) throws ArchiveException, IOException, NoSuchMethodException, SecurityException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		add(fcClass.getConstructor(AbstractFeature.class, AbstractID.class).newInstance(f, id));
	}
	
//	/**
//	 * @param fca		
//	 * @param fc	
//	 * @throws ArchiveException
//	 * @throws IOException
//	 */
//	public synchronized static  void add(File file, AbstractFeaturesCollector fc) throws ArchiveException, IOException {
//		if ( !file.exists() ) {
//			throw new ArchiveException("The FeaturesCollectorsArchive " + file.getAbsolutePath() + " does not exist");
//		}
//		RandomAccessFile rndFile = new RandomAccessFile(file, "rw");
//		
//		if (rndFile.readLong() != fileID) {
//			throw new IOException("The file ["+ file.getAbsolutePath() +"] does not appear to be a FeatureArchive");
//		}
//
//		int fileVersion = rndFile.readInt();
//
//		Class fcClass = null;
//		if (fileVersion > 1) {
//
//			// Reading classes
//			fcClass = FeaturesCollectors.readClass(rndFile);
//			
//		}
//		
//		rndFile.seek(rndFile.length());
//		
//		if (fcClass == null) {
//			FeaturesCollectors.writeData(rndFile, fc);
//		} else {
//			if (fcClass.isInstance(fc)) {
//				fc.writeData(rndFile);
//			} else {
//				throw new ArchiveException("FeaturesCollector class inserted ("
//						+ fc.getClass() + ") diffear from expected (" + fcClass
//						+ ")");
//			}
//		}
//		
//		
//	}
	
	public synchronized void add(AbstractFeaturesCollector fc) throws ArchiveException, IOException {

		if ( changed == false ) {
			lastSaveSize = size();
			changed = true;
		}
		
		int currPos = this.size();
		
		long fileLength = rndFile.length();
		
		addOffset(fileLength);
		
		synchronized ( rndFile ) {
			rndFile.seek(fileLength);
			
			if (idClass != null) {
				AbstractID id = ((IHasID) fc).getID();
				if (!idClass.isInstance(id)) {
					throw new ArchiveException("Object has a wrong ID class: "
							+ idClass + " requested, " + id.getClass() + " found.");
				}
				if ( this.contains(id) ) {
					throw new ArchiveException("ID " + id + " already exists in the archive");
				}
				ids.add(id);
				
				idPosMap.put(id, currPos);
			}
			
			if (fcClass == null) {
				FeaturesCollectors.writeData(rndFile, fc);
			} else {
				if (fcClass.isInstance(fc)) {
					fc.writeData(rndFile);
				} else {
					throw new ArchiveException("FeaturesCollector class inserted ("
							+ fc.getClass() + ") differ from expected (" + fcClass
							+ ")");
				}
			}
		}

		size++;
	}

	public final File getfile() {
		return f;
	}
	
	
	public final AbstractFeaturesCollector[] getAllArray() throws SecurityException, NoSuchMethodException, IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException, IOException {
		Collection<AbstractFeaturesCollector> tRes = getAll();
		AbstractFeaturesCollector[] res = new AbstractFeaturesCollector[tRes.size()];
		tRes.toArray(res);
		return res;
	}

	
	public final ArrayList<AbstractFeaturesCollector> getAll() {
		ArrayList<AbstractFeaturesCollector> arr = new ArrayList(size());
		for ( AbstractFeaturesCollector f : this) {
			arr.add(f);
		}
		return arr;
	}

	public static final AbstractFeaturesCollector[] getAllArray(String fileName) throws Exception {
		return getAllArray(new File(fileName));
	}
	
	public static final AbstractFeaturesCollector[] getAllArray(File file) throws Exception {
		FeaturesCollectorsArchive fca = new FeaturesCollectorsArchive(file, false);
		AbstractFeaturesCollector[] res = new AbstractFeaturesCollector[fca.size()];	 
		int i=0;
		for ( AbstractFeaturesCollector f : fca ) {
			res[i++] = f;
		} 
		return res;
	}
/*
	public static final ArrayList<AbstractFeaturesCollector> getAll(File file)
			throws IOException, SecurityException, NoSuchMethodException,
			IllegalArgumentException, InstantiationException,
			IllegalAccessException, InvocationTargetException {
		DataInputStream in = new DataInputStream(new BufferedInputStream(
				new FileInputStream(file)));
		ArrayList<AbstractFeaturesCollector> arr = null;

		if (in.readLong() != fileID) {
			System.err
					.println("The file does not appear to be a FeatureArchive");
		}

		int fileVersion = in.readInt();

		if (fileVersion > 1) {
			Class fcClass = FeaturesCollectors.readClass(in);
			Constructor fcClassConstructor = getFCConstructor(fcClass);
			new FeatureClassCollector(in);
			IDClasses.readClass_Int(in);

			arr = new ArrayList();
			while (in.available() != 0) {
				if (fcClassConstructor == null) {
					arr.add(FeaturesCollectors.readData(in));
				} else {
					arr.add((AbstractFeaturesCollector) fcClassConstructor.newInstance(in));
				}

			}
		} else {
			// old IO
			long indexOffSet = in.readLong();

			FeatureClassCollector featuresClasses = new FeatureClassCollector(
					in); // FeaturesCollectors.getClass( file.readInt() );
			Class idClass = IDClasses.readClass_Int(in);

			RandomAccessFile rndFile = new RandomAccessFile(file, "rw");
			rndFile.seek(indexOffSet);
			int size = rndFile.readInt();
			arr = new ArrayList(size);

			Log.info_verbose("--> The archive contains " + size);

			for (int i = 0; i < size; i++) {
				arr.add(FeaturesCollectors.readData(in));
			}
		}

		return arr;
	}
*/
	public final AbstractID getIdAt(int i) {
		return ids.get(i);
	}
	
	static final public void readHeader(DataInputStream in) throws IOException {
		
		in.readLong();
		int version = in.readInt();
		// Reading classes
		FeaturesCollectors.readClass(in);
		if ( version < 3)
			new FeatureClassCollector(in);
		IDClasses.readClass_Int(in);
	}
	
	public static FeaturesCollectorsArchive open(File file, boolean readIDs) throws SecurityException, NoSuchMethodException, IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException, IOException {
		return new FeaturesCollectorsArchive(file, readIDs);
	}
	
	public static FeaturesCollectorsArchive open(File file ) throws SecurityException, NoSuchMethodException, IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException, IOException {
		return new FeaturesCollectorsArchive(file );
	}

	public static int getSize(File file) throws SecurityException, NoSuchMethodException, IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException, IOException {
		FeaturesCollectorsArchive fca = new FeaturesCollectorsArchive(file, false);
		return fca.size();
	}
	
	public FeaturesCollectorsArchive(File file, boolean readIDs) throws IOException,
		SecurityException, NoSuchMethodException, IllegalArgumentException,
		InstantiationException, IllegalAccessException,
		InvocationTargetException {
		this ( file, readIDs, true);
	}
	
	public FeaturesCollectorsArchive(File file, boolean readIDs, boolean inMemoryOffsets) throws IOException,
			SecurityException, NoSuchMethodException, IllegalArgumentException,
			InstantiationException, IllegalAccessException,
			InvocationTargetException {

		//Log.info_verbose("Opening FeaturesCollectorsArchive: " + file.getAbsolutePath());
		if ( !file.exists()) {
			throw new IOException("The file ["+ file.getAbsolutePath() +"] was not found");
		}
		
		
		// LEGACY
		offsetFile = new File(getIDFileName_Legacy(file));
		idFile = new File(getOffsetFileName_Legacy(file));
		if ( !offsetFile.exists() ) offsetFile = new File(getOffsetFileName(file));
		if ( !idFile.exists() ) idFile = new File(getIDFileName(file));
		
		this.f = file;
		rndFile = new RandomAccessFile(file, "rw");
		
		if (rndFile.readLong() != fileID) {
			throw new IOException("The file ["+ file.getAbsolutePath() +"] does not appear to be a FeatureArchive");
		}

		int fileVersion = rndFile.readInt();

		
		
		
		if (fileVersion > 1) {

			// Reading classes
			fcClass = FeaturesCollectors.readClass(rndFile);
			fcClassConstructor = getFCConstructor(fcClass);
			fcClassConstructor_NIO = getFCConstructor_NIO(fcClass);
			
			if ( fileVersion == 2 ) {
				// legacy
				//featuresClasses = new FeatureClassCollector(rndFile);
				new FeatureClassCollector(rndFile);
			} else {
				// nothing is written
			}
			idClass = IDClasses.readClass_Int(rndFile);

			
			
			// OFFSET & ID FILES 
			if (    !offsetFile.exists() 
					|| file.lastModified() > offsetFile.lastModified()
					|| (readIDs && (
							!idFile.exists()
							|| file.lastModified() > idFile.lastModified()
						))
					){

				//rndFile_Offset = new RandomAccessFile(offsetFile, "rw");
				
				if (!offsetFile.exists())
					Log.info_verbose("Offsets file not found.");
				else if (file.lastModified() > offsetFile.lastModified())
					Log.info_verbose("offset file is out of date. Rebuilding...");
				if ( readIDs ) {
					if (!idFile.exists())
						Log.info_verbose("IDs file not found.");
					else if (file.lastModified() > idFile.lastModified())
						Log.info_verbose("IDs file is out of date. Rebuilding...");
				}
				
//				rndFile_Offset =  new RandomAccessFile(offsetFile, "rw");
//				rndFile_Offset.setLength(0);
				
				// Reading all
				Log.info_verbose("Analyzing archive... ");
//				if ( inMemoryOffsets )
//					offset = new TLongArrayList();
//				else
//					offset = null;
				if ( readIDs ) ids = new ArrayList();
				else ids = null;
				long coffset = 0;
				
				// reading
				int count=0;
				
				DataOutputStream out =
						new DataOutputStream (new  BufferedOutputStream (new FileOutputStream(offsetFile)));
				
				TimeManager tm = new TimeManager();
				while ((coffset = rndFile.getFilePointer()) < rndFile.length()) {
					out.writeLong(coffset);
					//addOffset(coffset);

					AbstractFeaturesCollector fc = null;
					if (fcClassConstructor == null) {
						fc = FeaturesCollectors.readData(rndFile);
					} else {
						fc = (AbstractFeaturesCollector) fcClassConstructor.newInstance(rndFile);
					}

					if ( readIDs ) ids.add(((IHasID) fc).getID());
					
					tm.reportProgress();
					//if ( ++count % 10000 == 0 ) Log.info_verbose(""+count);
				}
				out.close();
				Log.info_verbose("done");
				
				if ( readIDs ) {
					Log.info_verbose("Creating IDs HashTable... ");
					idPosMap = new HashMap(2 * count);
					for (int i = 0; i < count; i++) {
						idPosMap.put(ids.get(i), i);
					}
					createIDFile();
				} else {
					idPosMap = null;
				}
				Log.info_verbose("done");

				
			}
			
			// Always read everthing
			rndFile_Offset = new RandomAccessFile(offsetFile, "rw");
			
			// READING INDEX FILE
			//RandomAccessFile inOffset = new RandomAccessFile(offsetFile,"r");
			// RandomAccessFile inId = new RandomAccessFile( idFile, "r" );

			size = (int) (rndFile_Offset.length() / Long.BYTES);
			Log.info_verbose("Archive " + file.getAbsolutePath()
					+ " contains " + size + " objects.");

			// Reading offsets
			if ( inMemoryOffsets ) {
				
				byte[] byteArray = new byte[size * 8];
				
				rndFile_Offset.seek(0);
				rndFile_Offset.readFully(byteArray);
				
				long[] tempPositions = new long[size];
				LongBuffer inLongBuffer = ByteBuffer.wrap(byteArray).asLongBuffer();
				inLongBuffer.get(tempPositions, 0, size);
				
				offset = new TLongArrayList(tempPositions);					
			} else
				offset = null;

			if ( readIDs ) {
				DataInputStream idInput = new DataInputStream(new BufferedInputStream(new FileInputStream(idFile)));

				// Reading ids
				if (idClass != null) {
					
						// idOffsetMap = new HashMap(2*size);
						idPosMap = new HashMap(2 * size);
	
						Log.info_verbose("Reading IDs... ");
						ids = new ArrayList(Arrays.asList(IDClasses.readArray(idInput, size, idClass)));
						Log.info_verbose("done");
	
						Log.info_verbose("Reading IDs HashTable... ");
						size = size();
						for (int i = 0; i < size; i++) {
							// idOffsetMap.put(ids.get(i), positions.get(i));
							idPosMap.put(ids.get(i), i);
						}
						Log.info_verbose("done");
				} else {
					Log.info_verbose("--> The archive does not contains IDs");
					
					ids = null;
					idPosMap = null;
				}
			} else {
				ids = null;
				idPosMap = null;
			}



		} else  {
			throw new IOException("Old file format not supported");
		}
//
//			// VERSION 1
//			fcClass = null;
//			fcClassConstructor = null;
//			fcClassConstructor_NIO = null;
//			// old IO
//			long indexOffSet = rndFile.readLong();
//
//			new FeatureClassCollector(rndFile); 	// FeaturesCollectors.getClass(
//																	//	file.readInt()
//																	// );
//			// featureCollectorConstructor =
//			// featuresCollectorClass.getConstructor(DataInput.class);
//
//			idClass = IDClasses.readClass_Int(rndFile);
//
//			// file.seek(file.length()-8);
//			// long indexOffSet = file.readLong();
//			rndFile.seek(indexOffSet);
//			int size = rndFile.readInt();
//			//Log.info_verbose("--> The archive contains " + size);
//			//Log.info_verbose("--> Features Collector Class: " + fcClass);
//			//Log.info_verbose("--> Features to consider are: " + featuresClasses);
//
//			// Reading offsets
//
//			if ( inMemoryOffsets) {
//				long[] tempPositions = new long[size];
//				byte[] byteArray = new byte[size * 8];
//				LongBuffer inLongBuffer = ByteBuffer.wrap(byteArray).asLongBuffer();
//				rndFile.readFully(byteArray);
//				inLongBuffer.get(tempPositions, 0, size);
//				offset = new TLongArrayList(tempPositions);
//			} else
//				offset = null;
//			// Reading ids
//			if (idClass != null) {
//				// idOffsetMap = new HashMap(2*size);
//				idPosMap = new HashMap(2 * size);
//
//				Log.info_verbose("Reading IDs... ");
//				DataInputStream idInput = new DataInputStream(new BufferedInputStream(new FileInputStream(idFile)));
//				ids = new ArrayList(Arrays.asList(IDClasses.readArray(idInput, size, idClass)));
//				Log.info_verbose("done");
//
//				Log.info_verbose("Creating IDs HashTable... ");
//				size = size();
//				for (int i = 0; i < size; i++) {
//					// idOffsetMap.put(ids.get(i), positions.get(i));
//					idPosMap.put(ids.get(i), i);
//				}
//				Log.info_verbose("done");
//			} else {
//				Log.info_verbose("--> The archive does not contains IDs");
//				// no IDs
//				ids = null;
//				// idOffsetMap = null;
//				idPosMap = null;
//			}
//		}
		//Log.info_verbose(getInfo());
		

	}

	public static final String getIDFileName_Legacy(File file) {
		return file.getAbsolutePath() + ".ids";
	}

	public static final String getOffsetFileName_Legacy(File file) {
		return file.getAbsolutePath() + ".offs";
	}
	
	public static final String getIDFileName(File file) {
		return file.getAbsolutePath() + ".id";
	}

	public static final String getOffsetFileName(File file) {
		return file.getAbsolutePath() + ".off";
	}

	public void createIDFile() throws IOException {
		createIndexFiles(offsetFile, idFile, offset, ids);
	}
	
	public void updateIndexFiles() throws IOException {

		Log.info_verbose("Updating " + getfile().getAbsolutePath() + " index files");
		 
		DataOutputStream outIDs = new DataOutputStream(
				new BufferedOutputStream(new FileOutputStream(idFile, true)));

		for (int i = lastSaveSize; i < ids.size(); i++) {
			ids.get(i).writeData(outIDs);
		}

		outIDs.close();
		
		lastSaveSize = size();
	}
	
	public synchronized static void createIndexFiles(File offsetFile, File idFile, TLongArrayList positions, ArrayList<AbstractID> ids) throws IOException {
		
		Log.info_verbose("Creating index files");

		idFile.delete();

		DataOutputStream outIDs = null;
		if ( ids != null )
			outIDs = new DataOutputStream(
				new BufferedOutputStream(new FileOutputStream(idFile)));

		if ( outIDs != null ) {
			for (int i = 0; i < ids.size(); i++) {
				ids.get(i).writeData(outIDs);
			}
		}
		
		
		if ( outIDs != null )  outIDs.close();

		Log.info_verbose("Creating index files done");

		
	}
	
	public final AbstractFeaturesCollector getRandom() throws ArchiveException, IOException {
		return get(RandomOperations.getInt(this.size()));
	}

	public final synchronized AbstractFeaturesCollector get(int i) throws ArchiveException {
		try {
			
			rndFile.seek(getOffset(i));

			byte[] arr = null;
			if (i < size() - 1)
				arr = new byte[(int) (getOffset(i + 1) - getOffset(i))];
			else
				arr = new byte[(int) (rndFile.length() - getOffset(i))];


			if (fcClassConstructor_NIO == null) {
				 return FeaturesCollectors.readData(rndFile);

			} else {
				ByteBuffer buf = ByteBuffer.wrap(arr);
				rndFile.read(arr);
				return (AbstractFeaturesCollector) fcClassConstructor_NIO.newInstance(buf);
			}
		} catch (Exception e) {
			throw new ArchiveException(e);
		}

	}

	
	public final void addOffset(long v) throws IOException {		
		if ( offset != null ) {
			offset.add(v);
		}
		
		if ( rndFile_Offset != null ) {
			synchronized( rndFile_Offset ) {
				long cOff = rndFile_Offset.length();
				rndFile_Offset.seek(cOff);
				rndFile_Offset.writeLong(v);
			}
		}
	}
	
	public final long getOffset(int i) throws IOException {
		if ( offset!= null ) return offset.get(i);
		
		synchronized(rndFile_Offset) {
			rndFile_Offset.seek( i*Long.BYTES);
			return rndFile_Offset.readLong();
		}
	}
	
	

	public final Long getOffset(AbstractID id) throws IOException {

		return getOffset(idPosMap.get(id));
	}

	public synchronized final boolean contains(AbstractID id) throws ArchiveException {
		return idPosMap.get(id) != null;
	}

	public synchronized final AbstractFeaturesCollector get(AbstractID id)
			throws ArchiveException {
		Integer i = idPosMap.get(id);
		if (i == null)
			return null;
		return get(i);
	}

	public synchronized void close() throws IOException {
		if ( closed) return;
		rndFile.close();
		
		
		if ( changed ) {
			if ( lastSaveSize <= 0 ) {
				createIDFile();
			} else {
				updateIndexFiles();
			}
		}
		
		rndFile_Offset.close();
		closed = true;

	}

	@Override
	public Iterator<AbstractFeaturesCollector> iterator() {
		try {
			return new FeaturesCollectorsArchiveIterator(f, fcClassConstructor );
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public AbstractID[] getIDs() throws IOException {
		if ( ids != null ) {
			AbstractID[] res = new AbstractID[ids.size()];
			ids.toArray(res);
			return res;
		}
		DataInputStream idInput = new DataInputStream(new BufferedInputStream(new FileInputStream(idFile)));
		return IDClasses.readArray(idInput, size(), idClass);
		
	}
	
	
	// For kNN searching
	class InterDistances implements Runnable {
		private final int i1;
		private final AbstractFeaturesCollector[] objs;
		private final ISimilarity sim;
		private final double[][] d;
		
		InterDistances(AbstractFeaturesCollector[] objs, ISimilarity sim, double[][] d, int i1 ) {
			this.i1 = i1;
			this.objs = objs;
			this.sim = sim; 
			this.d = d;
		}

		@Override
		public void run() {
			AbstractFeaturesCollector obj1 = objs[i1];
			int di2=0;
			for ( int i2=i1; i2<objs.length; i2++) {
				d[i1][di2++] = sim.distance(obj1, objs[i2]);
			}
		}
	}
	
	public synchronized void writeInterdistances(File outFile, final IMetric sim ) throws IOException, ArchiveException, SecurityException, NoSuchMethodException, IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException {
		
		int size = size();
		long sizeSqr = (long) size*size;
		int nThread = ParallelOptions.reserveNFreeProcessors() +1 ;
		Log.info_verbose("N of distances to evaluate: " + sizeSqr);
		Log.info_verbose("Outfile final size: " + sizeSqr*8 / 1024 /1024+ " MegaBytes.");
		
		AbstractFeaturesCollector[] obj = new AbstractFeaturesCollector[size];
		Log.info("Reading all objects");
		this.getAll().toArray(obj);
		Log.info("Reading all objects DONE");
		
		Log.info("Allocating memory for distances");
		double[][] dists = new double[size][]; 
		for ( int i=0; i<dists.length; i++) {
			dists[i] = new double[size-i];
		}
		Log.info("Allocating memory for distances done");
		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(outFile)));
		
		long start = System.currentTimeMillis();
		
		Thread[] thread = new Thread[nThread];
		for ( int i1=0; i1<size; ) {
			int oldi1 = i1;
			for ( int ti=0; ti<nThread && i1<size; ti++ ) {
				thread[ti] = new Thread( new InterDistances(obj, sim, dists, i1++) ) ;
				thread[ti].start();
			}
	        
	        for ( int ti=0; ti<thread.length && thread[ti] != null; ti++ ) {
	        	try {
					thread[ti].join();
					thread[ti] = null;
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
	        }
	        
	        ParallelOptions.free(nThread-1);
	       
	        // saving output
	        for ( int ii1=oldi1; ii1<i1; ii1++ ) {	        	
	        	//  the first part of the row is taken for symmetry
	        	for ( int i2=0; i2<ii1; i2++) {
	        		out.writeDouble(dists[i2][ii1-i2]);
	        	}
	        	// second part of the row
	        	for ( int i2=ii1; i2<size; i2++) {
	        		out.writeDouble(dists[ii1][i2-ii1]);
	        	}
	        }
			
	        double avgTime = (double) (System.currentTimeMillis() - start) / ( i1 * size) ;
			Log.info_verbose((i1)*size + " done. Extimated time to finish: " + (avgTime*(size-i1)*size)/1000/60+ " min");
		}		
		out.close();
		
		/*
		Log.info_verbose("Testing:");
		RandomAccessFile rnd = new RandomAccessFile(outFile, "r");
		for (int i=0; i<100000; i++) {
			int i1 = RandomOperations.getInt(0, size-1);
			int i2 = RandomOperations.getInt(0, size-1);
			rnd.seek((long) (i1*size+i2)*8);
			double saved = rnd.readDouble();
			double actual = sim.distance(get(i1), get(i2));
			if ( saved != actual) {
				System.err.println("Saved distance (" + saved+") and actual (" + actual+")+ diffear");
			}
		}
		Log.info_verbose("Testing ended");*/
		
	}
	
	public String getInfo() {
		String tStr ="Archive " + f.getAbsolutePath() + "\n";
		tStr += "--> contains " + size() + "\n";
		tStr += "--> has features collector: " + fcClass + "\n";
		tStr += "--> has ID: " + idClass + "\n";
		return tStr;
	}
	
	public ArrayList<AbstractFeaturesCollector> get(AbstractID[] ids) throws ArchiveException {
		ArrayList<AbstractFeaturesCollector> res = new ArrayList<AbstractFeaturesCollector>();
		for ( AbstractID id : ids) {
			res.add(this.get(id));
		}
		return res;
	}

	public ArrayList<AbstractFeaturesCollector> get(ArrayList<AbstractID> queries) throws ArchiveException {
		ArrayList<AbstractFeaturesCollector> res = new ArrayList<AbstractFeaturesCollector>();
		for ( AbstractID q : queries) {
			AbstractFeaturesCollector c = this.get(q);
			if ( c == null ) {
				Log.info_verbose(q + " was not found");
			} else {
				res.add(c);
			}
		}
		return res;
	}
	
	public synchronized SimilarityResults[] getKNN(Collection<AbstractFeaturesCollector> qObj,
			int k, final ISimilarity sim, final boolean onlyID)
			throws IOException, SecurityException, NoSuchMethodException,
			IllegalArgumentException, InstantiationException,
			IllegalAccessException, InvocationTargetException, InterruptedException {
		AbstractFeaturesCollector[] tArr = new AbstractFeaturesCollector[qObj.size()];
		qObj.toArray(tArr);
		return getKNN(tArr, k, sim, onlyID);
	}
	
	public synchronized SimilarityResults[] getKNN(AbstractFeaturesCollector[] qObj,
			int k, final ISimilarity sim, final boolean onlyID)
			throws IOException, SecurityException, NoSuchMethodException,
			IllegalArgumentException, InstantiationException,
			IllegalAccessException, InvocationTargetException, InterruptedException {

		SimPQueueDMax[] queue = new SimPQueueDMax[qObj.length];
		for (int i = 0; i < queue.length; i++) {
			queue[i] = new SimPQueueDMax(k);
		}

		return getResults(qObj, queue, sim, onlyID);
	}
	
	
	public synchronized SimilarityResults[] getRange(Collection<AbstractFeaturesCollector> qObj,
			double range, final ISimilarity sim, final boolean onlyID)
			throws IOException, SecurityException, NoSuchMethodException,
			IllegalArgumentException, InstantiationException,
			IllegalAccessException, InvocationTargetException, InterruptedException {
		AbstractFeaturesCollector[] tArr = new AbstractFeaturesCollector[qObj.size()];
		qObj.toArray(tArr);
		return getRange(tArr, range, sim, onlyID);
	}
	
	public synchronized SimilarityResults[] getRange(AbstractFeaturesCollector[] qObj,
			double range, final ISimilarity sim, final boolean onlyID)
			throws IOException, SecurityException, NoSuchMethodException,
			IllegalArgumentException, InstantiationException,
			IllegalAccessException, InvocationTargetException, InterruptedException {

		SimPQueueDMax[] queue = new SimPQueueDMax[qObj.length];
		for (int i = 0; i < queue.length; i++) {
			queue[i] = new SimPQueueDMax(range);
		}


		return getResults(qObj, queue, sim, onlyID);
	}
	
	public synchronized SimilarityResults[] getOrdered(
			Collection<AbstractFeaturesCollector> qObj,
			final ISimilarity sim, final boolean onlyID)
			throws IOException, SecurityException, NoSuchMethodException,
			IllegalArgumentException, InstantiationException,
			IllegalAccessException, InvocationTargetException, InterruptedException {
		AbstractFeaturesCollector[] tArr = new AbstractFeaturesCollector[qObj.size()];
		qObj.toArray(tArr);
		return getOrdered(tArr, sim, onlyID);
	}
	
	public synchronized SimilarityResults[] getOrdered(
			AbstractFeaturesCollector[] qObj,
			final ISimilarity sim, final boolean onlyID)
			throws IOException, SecurityException, NoSuchMethodException,
			IllegalArgumentException, InstantiationException,
			IllegalAccessException, InvocationTargetException, InterruptedException {

		SimPQueueDMax[] queue = new SimPQueueDMax[qObj.length];
		for (int i = 0; i < queue.length; i++) {
			queue[i] = new SimPQueueDMax();
		}

		return getResults(qObj, queue, sim, onlyID);
	}
	
	public synchronized SimilarityResults[] getResults(
			AbstractFeaturesCollector[] qObj,
			SimPQueueDMax[] kNNQueue,
			final ISimilarity sim,
			final boolean onlyID) throws SecurityException, NoSuchMethodException, IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException, IOException, InterruptedException
	{

		FeaturesCollectorsArchiveSearch search = new FeaturesCollectorsArchiveSearch(this);
		search.search(qObj, kNNQueue, sim, onlyID);

		SimilarityResults[] res = new SimilarityResults[kNNQueue.length];
		for (int i = 0; i < kNNQueue.length; i++) {
			res[i] = kNNQueue[i].getResults();
			res[i].setQuery(qObj[i]);
		}

		return res;
	}
	
	
	public FeaturesCollectorsArchive shuffle(File outFile) throws Exception {
		
		return shuffle(this, outFile);

	}
	

	/**
	 * Shuffle objects in inArchive creating an archive at outFile.
	 * 
	 * @param in
	 * @param outFile
	 * @return
	 * @throws Exception 
	 */
	public static FeaturesCollectorsArchive shuffle(FeaturesCollectorsArchive inArchive, File outFile) throws Exception {
		FeaturesCollectorsArchive res = inArchive.getSameType(outFile);
		
		int size = inArchive.size();
	    int[] ids = RandomOperations.getRandomOrderedInts(0, size);
	    
	    TimeManager tm = new TimeManager();
	    for (int i=0; i<size; i++) {
	    	res.add(inArchive.get(ids[i]));
	    	Log.info_verbose_progress(tm, i, size);
	    }
		
		return res;
	}
	
	/**
	 * Shuffle objects in inArchive creating an archive at outFile.
	 * 
	 * @param in
	 * @param outFile
	 * @return
	 * @throws Exception 
	 */
	public static void shuffle(FeaturesCollectorsArchives inArchives, File outFile) throws Exception {
		FeaturesCollectorsArchive first = inArchives.getArchive(0);
		FeaturesCollectorsArchive_Buffered bufferedOut
			= FeaturesCollectorsArchive_Buffered.create(outFile,  first.getIDClass(), first.getFcClass() );
				
		int size = inArchives.size();

		int[] ids = RandomOperations.getRandomOrderedInts(0, size);
	    
	    TimeManager tm = new TimeManager();
	    for (int i=0; i<size; i++) {
	    	//Log.info_verbose(i + "\t" + ids[i]);
	    	bufferedOut.add(inArchives.get(ids[i]));
	    	Log.info_verbose_progress(tm, i, size);
	    }
		
	    bufferedOut.close();
	}
	
	/**
	 * @param lfGroupClass	Class of the requested local features group
	 * @return
	 * @throws IOException 
	 */
	public int getNumberOfLocalFeatures(Class<? extends ALocalFeaturesGroup> lfGroupClass) {
			
		int res = 0;
		int i=0;
		Log.info_verbose("Counting elements of " + lfGroupClass + " in the archive.");
		
		TimeManager tm = new TimeManager();	
		for ( AbstractFeaturesCollector curr : this ) {
			i++;
			res += curr.getFeature(lfGroupClass).size();
			if ( tm.hasToOutput() ) {
				Log.info_verbose("\t" + res + " lf\t" + tm.getProgressString(i, size()));
			}
		}
		Log.info_verbose(res + " Local Features were found"  );
		return res;
	}
	
	public ArrayList<ALocalFeature> getRandomLocalFeatures(Class<? extends ALocalFeaturesGroup> lfGroupClass, int maxNObjs ) {
		return getRandomLocalFeatures(lfGroupClass, maxNObjs, false);
	}
	
	public ArrayList<ALocalFeature> getRandomLocalFeatures(Class<? extends ALocalFeaturesGroup> lfGroupClass, int maxNObjs, boolean removeKeyPoint ) {
		if ( maxNObjs < 0 ) return getRandomLocalFeatures(lfGroupClass, 1.0, removeKeyPoint);
		
		int nLF_archive = getNumberOfLocalFeatures(lfGroupClass);
		double prob = maxNObjs / (double) nLF_archive;
		if ( prob > 1.0  ) prob = 1.0;
		return getRandomLocalFeatures(lfGroupClass, prob, removeKeyPoint);
	}
	
	public ArrayList<ALocalFeature> getRandomLocalFeatures(Class<? extends ALocalFeaturesGroup> lfGroupClass, int maxNObjs, boolean removeKeyPoint , File out) throws Exception {
		if ( maxNObjs < 0 ) return getRandomLocalFeatures(lfGroupClass, 1.0, removeKeyPoint,out);
		int nLF_archive = getNumberOfLocalFeatures(lfGroupClass);
		double prob = maxNObjs / (double) nLF_archive;
		if ( prob > 1.0  ) prob = 1.0;
		return getRandomLocalFeatures(lfGroupClass, prob, removeKeyPoint,out);
	}
	public ArrayList<ALocalFeature> getRandomLocalFeatures(Class<? extends ALocalFeaturesGroup> lfGroupClass, double prob, boolean removeKeyPoint, File out) throws Exception {
		Log.info_verbose("Getting random elements of " + lfGroupClass + " with probability " + prob);
		FeaturesCollectorsArchive learningPoint = FeaturesCollectorsArchive.create(out);
		int i=0;
		
		ArrayList<ALocalFeature> res = new ArrayList<ALocalFeature>();
		TimeManager tm = new TimeManager();
		for ( AbstractFeaturesCollector currFC : this ) {
			i++;
			ALocalFeaturesGroup currGroup = currFC.getFeature(lfGroupClass);
			if ( currGroup == null ) continue;
			ArrayList<ALocalFeature> selectedPoint = new ArrayList<ALocalFeature>();// lucia
			AbstractID id = ((IHasID) currFC).getID();
			for ( ALocalFeature currLF : currGroup.lfArr ) {
				if ( RandomOperations.trueORfalse(prob)) {
					selectedPoint.add(currLF.getUnlinked());//
					
					if ( removeKeyPoint ) res.add(currLF.unlinkLFGroup().removeKP());
					else res.add(currLF.unlinkLFGroup());
				}				
			}
			if (!selectedPoint.isEmpty()) {// lucia

				ALocalFeature[] lfarr = selectedPoint.toArray(new ALocalFeature[1]);
				currGroup.lfArr = lfarr;
				FeaturesCollectorArr fcSelectedPoints = new FeaturesCollectorArr(currGroup, id);

				learningPoint.add(fcSelectedPoints);// lucia
			}
			if ( tm.hasToOutput() ) {
				Log.info_verbose(" - " + res.size() + "\tlfs " + tm.getProgressString(i, this.size()));
			}
		}
		
		Log.info_verbose( res.size() + " local features were randomly selected");
		learningPoint.close();
		return res;
	}

	public ArrayList<ALocalFeature> getRandomLocalFeatures(Class<? extends ALocalFeaturesGroup> lfGroupClass, double prob, boolean removeKeyPoint) {
		Log.info_verbose("Getting random elements of " + lfGroupClass + " with probability " + prob);
		
		int i=0;
		
		ArrayList<ALocalFeature> res = new ArrayList<ALocalFeature>();
		TimeManager tm = new TimeManager();
		for ( AbstractFeaturesCollector currFC : this ) {
			i++;
			ALocalFeaturesGroup currGroup = currFC.getFeature(lfGroupClass);
			if ( currGroup == null ) continue;
			
			for ( ALocalFeature currLF : currGroup.lfArr ) {
				if ( RandomOperations.trueORfalse(prob)) {
					if ( removeKeyPoint ) res.add(currLF.unlinkLFGroup().removeKP());
					else res.add(currLF.unlinkLFGroup());
				}				
			}
			if ( tm.hasToOutput() ) {
				Log.info_verbose(" - " + res.size() + "\tlfs " + tm.getProgressString(i, this.size()));
			}
		}
		
		Log.info_verbose( res.size() + " local features were randomly selected");
		
		return res;
	}
	
	public ArrayList<AbstractFeature> getRandomFeatures(Class<? extends AbstractFeature> featureClass, int maxNObjs ) throws IOException {
		double prob = maxNObjs / (double) size();
		if ( prob > 1.0 ) prob = 1.0;
		return getRandomFeatures(featureClass, prob);
	}

	
	public ArrayList<AbstractFeature> getRandomFeatures(Class<? extends AbstractFeature> featureClass, int maxNObjs, File out ) throws Exception {
		double prob = maxNObjs / (double) size();
		if ( prob > 1.0 ) prob = 1.0;
		return getRandomFeatures(featureClass, prob, out);
	}
	
	public ArrayList<AbstractFeaturesCollector> getRandom(double prob) {
		Log.info_verbose("Getting random elements  with probability " + prob);
		
		int i=0;
		
		ArrayList<AbstractFeaturesCollector> res = new ArrayList<AbstractFeaturesCollector>();
		TimeManager tm = new TimeManager();
		for ( AbstractFeaturesCollector currFC : this ) {
			i++;
			
			if ( RandomOperations.trueORfalse(prob)) {
				res.add(currFC);
			}				
			
			if ( tm.hasToOutput() ) {
				Log.info_verbose(" - " + res.size() + "\tlfs " + tm.getProgressString(i, this.size()));
			}
		}
		
		Log.info_verbose( res.size() + " features were randomly selected");
		
		return res;
	}
	
	public ArrayList<AbstractFeature> getRandomFeatures(Class<? extends AbstractFeature> featureClass, double prob) {
		Log.info_verbose("Getting random elements of " + featureClass + " with probability " + prob);
		
		int i=0;
		
		ArrayList<AbstractFeature> res = new ArrayList<AbstractFeature>();
		TimeManager tm = new TimeManager();
		for ( AbstractFeaturesCollector currFC : this ) {
			i++;
			AbstractFeature currF = currFC.getFeature(featureClass);
			
			if ( currF == null ) {
				if ( currFC instanceof IHasID) {
					Log.info_indent( "fc " + i + "[id: " + ((IHasID)currFC).getID() + "] does not contain requested feature");
				} else {
					Log.info_indent( "fc " + i +" does not contain requeste feature");
				}
			
			}
			
			if ( RandomOperations.trueORfalse(prob)) {
				res.add(currF.unlinkFC());
			}				
			
			if ( tm.hasToOutput() ) {
				Log.info_verbose(" - " + res.size() + "\tlfs " + tm.getProgressString(i, this.size()));
			}
		}
		
		Log.info_verbose( res.size() + " features were randomly selected");
		
		return res;
	}
	
	public synchronized void finalize() {
		
		try {
			close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public ArrayList<AbstractFeature> getRandomFeatures(Class<? extends AbstractFeature> featureClass, double prob, File out) throws Exception {
		Log.info_verbose("Getting random elements of " + featureClass + " with probability " + prob);
				int i=0;
		FeaturesCollectorsArchive learningPoints = FeaturesCollectorsArchive.create(out);
		ArrayList<AbstractFeature> res = new ArrayList<AbstractFeature>();
		TimeManager tm = new TimeManager();
		for ( AbstractFeaturesCollector currFC : this ) {
			i++;
			AbstractFeature currF = currFC.getFeature(featureClass);
			
			if ( currF == null ) {
				if ( currFC instanceof IHasID) {
					Log.info_indent( "fc " + i + "[id: " + ((IHasID)currFC).getID() + "] does not contain requested feature");
				} else {
					Log.info_indent( "fc " + i +" does not contain requeste feature");
				}
			
			}
			
			if ( RandomOperations.trueORfalse(prob)) {
				res.add(currF.unlinkFC());
				learningPoints.add(currFC);// 
			}				
			
			if ( tm.hasToOutput() ) {
				Log.info_verbose(" - " + res.size() + "\tlfs " + tm.getProgressString(i, this.size()));
			}
		}
		
		Log.info_verbose( res.size() + " features were randomly selected");
		learningPoints.close();
		return res;
	}

	
}
