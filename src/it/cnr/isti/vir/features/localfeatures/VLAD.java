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
package it.cnr.isti.vir.features.localfeatures;

import it.cnr.isti.vir.distance.L2;
import it.cnr.isti.vir.features.AbstractFeature;
import it.cnr.isti.vir.features.AbstractFeaturesCollector;
import it.cnr.isti.vir.features.IArrayValues;
import it.cnr.isti.vir.features.IByteValues;
import it.cnr.isti.vir.features.IFloatValues;
import it.cnr.isti.vir.features.IIntValues;
import it.cnr.isti.vir.features.IUByteValues;
import it.cnr.isti.vir.features.bof.LFWords;
import it.cnr.isti.vir.global.Log;
import it.cnr.isti.vir.pca.PrincipalComponents;
import it.cnr.isti.vir.similarity.ISimilarity;
import it.cnr.isti.vir.util.WorkingPath;
import it.cnr.isti.vir.util.bytes.FloatByteArrayUtil;
import it.cnr.isti.vir.util.math.Normalize;
import it.cnr.isti.vir.util.math.VectorMath;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Properties;

public class VLAD extends AbstractFeature implements IFloatValues {

	public static LFWords ref = null;
	
	public static LFWords getReferences() {
		return ref;
	}

	public static void setReferences(LFWords ref) {
		VLAD.ref = ref;
	}

	public static void setReferences(File file) throws Exception {
		setReferences( new LFWords(file) );
		Log.info_verbose("VLAD References were set using " + file.getAbsolutePath());
	}
	
	public static PrincipalComponents getPCAProjection() {
		return pca;
	}

	public static void setPCAProjection(PrincipalComponents pca) {
		VLAD.pca = pca;
	}


	public static PrincipalComponents pca = null;
	
	public AbstractFeaturesCollector linkedFC;
	float[] values;
	
	@Override
	public final int getLength() {
		return values.length;
	}
	
	@Override
	public final float[] getValues() {
		return values;
	}
	
	public int size() {
		if ( values == null ) return 0;
		return values.length;
	}
	
	public final void init(Properties prop) throws Exception {
		String refFileName = prop.getProperty("VLAD.references");
		if ( refFileName != null ) {
			setReferences(WorkingPath.getFile(refFileName));
		}
		
		String pcaFileName = prop.getProperty("VLAD.PCA");
		if ( pcaFileName != null ) {
			setReferences(WorkingPath.getFile(pcaFileName));
		}
		
		
	}
	
	@Override
	public void writeData(DataOutput out) throws IOException {
		if ( values == null ) {
			out.writeInt( 0);
		} else {
			out.writeInt( values.length);
			byte[] b = new byte[FloatByteArrayUtil.BYTES*values.length];
			FloatByteArrayUtil.convToBytes(values, b, 0);
			out.write(b);
		}
	}
	
	public void writeData(ByteBuffer buff) throws IOException {
		if ( values == null ) {
			buff.putInt( 0);
		} else {
			buff.putInt( values.length);
			for ( int i=0; i<values.length; i++ )
				buff.putFloat(values[i]);
		}
	}
	
    public VLAD(ByteBuffer in ) throws Exception {
        this(in, null);
    }
	
	public VLAD(ByteBuffer in, AbstractFeaturesCollector fc ) throws Exception {
		int size = in.getInt();
		linkedFC = fc;
		if ( size != 0 ) { 
			values = new float[size];
			for ( int i=0; i<values.length; i++ ) {
				values[i] = in.getFloat();
			}
		}	
	}
	
	public VLAD(DataInput in, AbstractFeaturesCollector fc ) throws Exception {
		
		int size = in.readInt();

		if ( size != 0 ) {
			int nBytes = Float.BYTES*size;
			byte[] bytes = new byte[nBytes];
			in.readFully(bytes);
			values = FloatByteArrayUtil.get(bytes, 0, size);
		}
    }
	
	public VLAD(float[] values) {
		this.values = values;
	}

	public VLAD(DataInput in ) throws Exception {
		this(in, null);
	}


//	public static final VLAD getVLAD(ALocalFeaturesGroup features ) throws Exception {
//		if ( ref == null ) {
//			return getVLAD(features, ref);
//		} 
//		throw new Exception("No references have been defined for VLAD");
//	}
	
	public static final  VLAD getVLAD(ALocalFeaturesGroup features, LFWords fWords ) throws Exception {
		return getVLAD(features, fWords, 0.2, false, false);
	}
	
	public static final  VLAD getVLAD(ALocalFeaturesGroup features, LFWords fWords, boolean intranorm ) throws Exception {
		if ( intranorm ) 
			return getVLAD(features, fWords, null, true, false);
		else
			return getVLAD(features, fWords, 0.2, false, false);
	}
	
    public static final  VLAD getVLAD(
    		ALocalFeaturesGroup features, LFWords fWords,
    		Double a, boolean intranorm, boolean residualNormalization) throws Exception {
	
    	ALocalFeature[] lf  = features.getLocalFeatures();

    	if ( lf.length == 0 ) {
        	throw new Exception( "VLAD can't be computed for images with 0LFs " + features.getClass() );
        }
        
    	return getVLAD(lf, fWords, a, intranorm, residualNormalization);
    }
	
    public static final  VLAD getVLAD(
    		AbstractFeature[] lf, LFWords fWords) throws Exception {
    	return getVLAD(lf, fWords, 0.2, false, false);
    }
    
    public static final  VLAD getVLAD(
    		AbstractFeature[] data, AbstractFeature[]  refs, ISimilarity sim) throws Exception {
    	return getVLAD(data, refs, sim, 0.2, false, false);
    }
    
    public static final  VLAD getVLAD(
    		AbstractFeature[] lf, LFWords fWords,
    		Double a, boolean intranorm, boolean residualNormalization) throws Exception {
    	return getVLAD(lf, (AbstractFeature[]) fWords.getFeatures(), fWords.getSimilarity(), 0.2, false, false);
    }
    
	private static final int getNNIndex(AbstractFeature[] ref, AbstractFeature feature, ISimilarity sim ) {
		double nnDist = sim.distance(feature, ref[0]);
		int nnIndex = 0;
		for (int i = 1; i < ref.length; i++) {
			double temp = sim.distance(feature, ref[i], nnDist);
			if (temp >= 0 && temp < nnDist) {
				nnIndex = i;
				nnDist = temp;
			}
		}
		return nnIndex;
	}
    
    public static final  VLAD getVLAD(
    		AbstractFeature[] lf, AbstractFeature[] refs, ISimilarity sim,
    		Double a, boolean intranorm, boolean residualNormalization) throws Exception {
   
    	
    	
        if ( ! (refs[0] instanceof IArrayValues) ) {
        	throw new Exception( "VLAD can't be computed for " + refs[0].getClass() );
        }
    	
        int d = ((IArrayValues) refs[0]).getLength(); 
        
        int size = refs.length * d;
        	
        float[] values = null;
        

        
        if ( refs[0] instanceof IFloatValues ) {
        	values = new float[size];
        	
        	float[] temp  = null;
        	if ( residualNormalization ) temp = new float[d];
        	
			for (int iLF = 0; iLF < lf.length; iLF++) {

				float[] curr = ((IFloatValues) lf[iLF]).getValues();
				int iW = getNNIndex(refs, lf[iLF], sim);
				int start = iW * d;
				int end = start + d;
				float[] ref = ((IFloatValues) refs[iW]).getValues();
					
				if ( residualNormalization ) {
					
					float norm = 0;
					for (int i=0; i<d; i++ ) {
						temp[i] = curr[i] - ref[i];
						norm += temp[i]*temp[i];
					}
					
					if ( norm > 0.0F ) {
						norm = (float) Math.sqrt( norm );
						for (int i=start, j=0; i < end; i++, j++) {
							values[i] += temp[j] / norm;
						}
					}
				} else {
					for (int i=start, j=0; i < end; i++, j++) {
						values[i] += curr[j] - ref[j];
					}
				}
			}

			
		} else if ( refs[0] instanceof IByteValues  ) {
			
			int[] intValues = new int[size];
			
			int[] temp  = null;
        	if ( residualNormalization ) temp = new int[d];
			
			for (int iLF = 0; iLF < lf.length; iLF++) {
				byte[] curr = ((IByteValues) lf[iLF]).getValues();
				
				int iW = getNNIndex(refs, lf[iLF], sim);
				int start = iW * d;
				int end = start + d;
				
				byte[] ref = ((IByteValues) refs[iW]).getValues();
				
				if ( residualNormalization ) {
					
					int intNorm = 0;
					for (int i=0; i<d; i++ ) {
						temp[i] = curr[i] - ref[i];
						intNorm += temp[i]*temp[i];
					}
					
					if ( intNorm > 0 ) {
						float norm = (float) Math.sqrt((float) intNorm);
						for (int i=start, j=0; i < end; i++, j++) {
							values[i] += temp[j] / norm;
						}
					}
				} else {
					int j = 0;
					for (int i = start; i < end; i++, j++) {
						intValues[i] += curr[j] - ref[j];
					}
				}
			
			}
			
			values = VectorMath.getFloats(intValues);
							        
		} else if ( refs[0] instanceof IIntValues  ) {
			
			int[] intValues = new int[size];
			
			int[] temp  = null;
	    	if ( residualNormalization ) temp = new int[d];
			
			for (int iLF = 0; iLF < lf.length; iLF++) {
				int[] curr = ((IIntValues) lf[iLF]).getValues();
				
				int iW = getNNIndex(refs, lf[iLF], sim);
				int start = iW * d;
				int end = start + d;
				
				int[] ref = ((IIntValues) refs[iW]).getValues();
				
				if ( residualNormalization ) {
					
					int intNorm = 0;
					for (int i=0; i<d; i++ ) {
						temp[i] = curr[i] - ref[i];
						intNorm += temp[i]*temp[i];
					}
					
					if ( intNorm > 0 ) {
						float norm = (float) Math.sqrt((float) intNorm);
						for (int i=start, j=0; i < end; i++, j++) {
							values[i] += temp[j] / norm;
						}
					}
				} else {
					int j = 0;
					for (int i = start; i < end; i++, j++) {
						intValues[i] += curr[j] - ref[j];
					}
				}
			
			}
			
			values = VectorMath.getFloats(intValues);
						        
		} else if (  refs[0] instanceof IUByteValues ) {
			
			int[] intValues = new int[size];
			
			int[] temp  = null;
        	if ( residualNormalization ) temp = new int[d];
			
			for (int iLF = 0; iLF < lf.length; iLF++) {

				byte[] curr = ((IUByteValues) lf[iLF]).getValues();

				int iW = getNNIndex(refs, lf[iLF], sim );
				int start = iW * d;
				int end = start + d;

				byte[] ref = ((IUByteValues) refs[iW]).getValues();

				if ( residualNormalization ) {
					
					int intNorm = 0;
					for (int i=0; i<d; i++ ) {
						temp[i] = curr[i] - ref[i];
						intNorm += temp[i]*temp[i];
					}
					
					if ( intNorm > 0 ) {
						float norm = (float) Math.sqrt((float) intNorm);
						for (int i=start, j=0; i < end; i++, j++) {
							values[i] += temp[j] / norm;
						}
					}
				} else {
					int j = 0;
					for (int i = start; i < end; i++, j++) {
						intValues[i] += curr[j] - ref[j];
					}
				}
				
				
			}
			
			values = VectorMath.getFloats(intValues);
							        
		} else {
        	throw new Exception( "VLAD can't be computed for " + refs[0].getClass() );
		}
        
        
        
        if ( intranorm) {
        	
        	/* INTRANORM 
        	 * See "All about VLAD" paper
        	 */
        	for ( int i=0; i<refs.length; i++ ) {
        		int start = d * i;
        		int end = start + d;
        		Normalize.l2(values, start, end);         		
        	}
        	
        	
        }
        
        if ( a == null ) {
           	/* SSR */
           	// SSR has been proposed in 2012
           	// "Negative evidences and co-occurrences in image retrieval: the benefit of PCA and whitening"
    		Normalize.ssr(values);
    	} else {

    		Normalize.sPower(values, a);
    	}
        
        
        // L2 Normalization (performed in any case)
        Normalize.l2(values);        	
        
        
        return new VLAD(values);
    }
    
    
    /*
	public static final VLAD gVLAD(ALocalFeaturesGroup group, LFWords words) throws Exception {
		if ( group instanceof SIFTGroup ) {
			return getVLAD((SIFTGroup) group, words);
		}
		return null;
	}*/
	
	

	public static final double getDistance(VLAD s1, VLAD s2, double max ) {
		return getDistance(s1, s2);
	}


	public static final double getDistance(VLAD s1, VLAD s2 ) {
		
		float[] v1 = s1.values;
		float[] v2 = s2.values;
		
		if ( v1 == null && v2 == null ) return 0;
		if ( v1 == null || v2 == null ) return 1.0; // TODO !!!
		
		if ( v1.length != v2.length ) return 1.0;
		
//		double t = 0.0;
//		for ( int i=0; i<s1.size(); i++ ) {
//			t += v1[i] * v2[i];
//		}
//		double dist =  (1.0 - t) / 2.0;
//		
//		//if ( dist < 0.0 ) dist = 0.0;
//
//		return dist;
		
		// for dealing with empty VLAD
//		double sum1 = 0.0;
//		double sum2 = 0.0;
//		for ( int i=0; i<v1.length; i++ ) {
//			sum1 += v1[i];
//			sum2 += v2[i];
//		}
//		
//		if ( sum1 == 0.0 && sum2 == 0.0 ) return 0.0; 
//		if ( sum1 == 0.0 || sum2 == 0.0 ) return Math.sqrt(0.5);
		
		//if ( sum1 != 0.0 && (sum1 < .999f || sum1 > 1.001f)) System.out.println(sum1);
		//if ( sum2 != 0.0 && (sum2 < .999f || sum2 > 1.001f)) System.out.println(sum2);
		
		return L2.get(v1, v2)/2.0;
	}


	public 	String toString() {
		return Arrays.toString(values);
	}

}
