package com.voodoo.aquasmart;

//import com.radiy.smartlight.general.CRC16MirrANSI;

import android.support.annotation.NonNull;


/**
* ����� ������������� ������ ������
* @author Ostapenko
*
*/
public class DataFrame implements IDataFrame
{
	// ����������� ������ ������ (�����[2] + �������[1] + CRC[2])
	private static final int MIN_FRAME_SIZE = 5;
	
	// ������ ���������� �������� ����� ����� ������
	private static final int L_FLEN_INDEX = 0;
	// ������ ���������� �������� ����� ����� ������
	private static final int H_FLEN_INDEX = 1;
	
	// ������ ���������� �������
	private static final int COMMAND_INDEX = 2;
	// ������ ������ ���������� ������
	private static final int START_DATA_INDEX = COMMAND_INDEX + 1;
	
	private static final int CORRECT_FRAME = 1;
	private static final int INCORRECT_FRAME = 2;
	
	// ������ ������
	private byte[] data;
	private int frameState = INCORRECT_FRAME;
	
	/**
	 * �����������
	 * @param command �������
	 * @param commandData ������ �������
	 */
//	public DataFrame(/*Commands command,*/ @NonNull byte[] commandData)
//	{
//		data = new byte[commandData.length + MIN_FRAME_SIZE];
////		data[L_FLEN_INDEX] = (byte)(data.length & 0xFF);
////		data[H_FLEN_INDEX] = (byte)(data.length >> 8);
////		data[COMMAND_INDEX] = command.getCode();
////
////		for (int index = 0; index < commandData.length; index++)
////			data[START_DATA_INDEX + index] = commandData[index];
////
////		int crc = CRC16MirrANSI.Calculate(data, 0, data.length - 2, CRC16MirrANSI.DEFAULT_START_CRC);
////		data[data.length - 2] = (byte)(crc & 0xFF);
////		data[data.length - 1] = (byte)(crc >> 8);
//
//		frameState = CORRECT_FRAME;
//	}
	
	/**
	 * �����������
	 * @param data ������ ������
	 */
	public DataFrame(@NonNull byte[] data)
	{
		this.data = data.clone();
		
//		if (data.length >= MIN_FRAME_SIZE)
//		{
//			int inputCRC = ((int)data[data.length - 2] & 0xFF) | (((int)data[data.length - 1] & 0xFF) << 8);
//			int calcCRC = CRC16MirrANSI.Calculate(data, 0, data.length - 2, CRC16MirrANSI.DEFAULT_START_CRC);
//			if (inputCRC == calcCRC)
//				frameState = CORRECT_FRAME;
//		}
	}
	
	/**
	 * �������� ��� �������
	 * @return
	 */
//	public void getCommand()
//	{
//		return Commands.valueOf(data[COMMAND_INDEX]);
//	}
	
	/**
	 * �������� ������ ������ ������
	 * @return
	 */
	public byte[] getFrameData()
	{
		return data.clone();
	}
	
	/**
	 * �������� ������ ����� ������
	 * @return
	 */
	public int getFrameLength()
	{
		return data.length;
	}

	/**
	 * �������� "������" ������ ������ (��� ��� ����� � CRC)
	 * @return
	 */
	public byte[] getPureData()
	{
		byte[] result = new byte[data.length - MIN_FRAME_SIZE];
		for(int index = 0; index < result.length; index++)
			result[index] = data[START_DATA_INDEX + index];
		
		return result;
	}
	
	/**
	 * ������������ ������
	 * @return false - ����� ������ ������ ���������� ��� �� ��������� CRC
	 */
	public boolean isCorrect()
	{
		return (frameState == CORRECT_FRAME);
	}
}