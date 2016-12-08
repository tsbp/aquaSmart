package com.voodoo.aquasmart;

/**
 * ��������� ������ ������
 * @author Ostapenko
 *
 */
public interface IDataFrame
{
	/**
	 * �������� ��� �������
	 * @return
	 */
	//public Commands getCommand();
	
	/**
	 * �������� ������ ������ ������
	 * @return
	 */
	public byte[] getFrameData();
	
	/**
	 * �������� ������ ����� ������
	 * @return
	 */
	public int getFrameLength();
	
	/**
	 * �������� "������" ������ ������ (��� ��� ����� � CRC)
	 * @return
	 */
	public byte[] getPureData();
	
	/**
	 * ������������ ������
	 * @return false - ����� ������ ������ ���������� ��� �� ��������� CRC
	 */
	public boolean isCorrect();
}