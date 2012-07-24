<?php

/**
 * This is the model class for table "traceper_friends".
 *
 * The followings are the available columns in table 'traceper_friends':
 * @property integer $Id
 * @property string $friend1
 * @property integer $friend1Visibility
 * @property string $friend2
 * @property integer $friend2Visibility
 * @property integer $status
 *
 * The followings are the available model relations:
 * @property TraceperUsers $friend10
 * @property TraceperUsers $friend20
 */
class Friends extends CActiveRecord
{
	/**
	 * Returns the static model of the specified AR class.
	 * @return Friends the static model class
	 */
	public static function model($className=__CLASS__)
	{
		return parent::model($className);
	}

	/**
	 * @return string the associated database table name
	 */
	public function tableName()
	{
		return 'traceper_friends';
	}

	/**
	 * @return array validation rules for model attributes.
	 */
	public function rules()
	{
		// NOTE: you should only define rules for those attributes that
		// will receive user inputs.
		return array(
			array('friend1, friend1Visibility, friend2, friend2Visibility', 'required'),
			array('friend1Visibility, friend2Visibility, status', 'numerical', 'integerOnly'=>true),
			array('friend1, friend2', 'length', 'max'=>11),
			// The following rule is used by search().
			// Please remove those attributes that should not be searched.
			array('Id, friend1, friend1Visibility, friend2, friend2Visibility, status', 'safe', 'on'=>'search'),
		);
	}

	/**
	 * @return array relational rules.
	 */
	public function relations()
	{
		// NOTE: you may need to adjust the relation name and the related
		// class name for the relations automatically generated below.
		return array(
			'friend10' => array(self::BELONGS_TO, 'TraceperUsers', 'friend1'),
			'friend20' => array(self::BELONGS_TO, 'TraceperUsers', 'friend2'),
		);
	}

	/**
	 * @return array customized attribute labels (name=>label)
	 */
	public function attributeLabels()
	{
		return array(
			'Id' => 'ID',
			'friend1' => 'Friend1',
			'friend1Visibility' => 'Friend1 Visibility',
			'friend2' => 'Friend2',
			'friend2Visibility' => 'Friend2 Visibility',
			'status' => 'Status',
		);
	}

	/**
	 * Retrieves a list of models based on the current search/filter conditions.
	 * @return CActiveDataProvider the data provider that can return the models based on the search/filter conditions.
	 */
	public function search()
	{
		// Warning: Please modify the following code to remove attributes that
		// should not be searched.

		$criteria=new CDbCriteria;

		$criteria->compare('Id',$this->Id);
		$criteria->compare('friend1',$this->friend1,true);
		$criteria->compare('friend1Visibility',$this->friend1Visibility);
		$criteria->compare('friend2',$this->friend2,true);
		$criteria->compare('friend2Visibility',$this->friend2Visibility);
		$criteria->compare('status',$this->status);

		return new CActiveDataProvider($this, array(
			'criteria'=>$criteria,
		));
	}
	
	public function makeFriends($friend1, $friend2){
		$friends = new Friends();
		$friends->friend1 = $friend1;
		$friends->friend1Visibility = 1; //default visibility setting is visible
		$friends->friend2 = $friend2;
		$friends->friend2Visibility = 1; //default visibility setting is visible
		$friends->status = 1;		
	
		return $friends->save();
	}	
	
	
	public function makeFriendsVisibilities($friend1, $friend2, $allowToSeeMyPosition){
		
		$errorOccured = true;
		
		$friendship = Friends::model()->find(array('condition'=>'(friend1=:friend1Id AND friend2=:friend2Id) OR (friend1=:friend2Id AND friend2=:friend1Id)',
				'params'=>array(':friend1Id'=>$friend1,
						':friend2Id'=>$friend2
				)
		)
		);
		if($friendship != null)
		{
			if($friendship->friend1 == $friend1)
			{
				$friendship->friend1Visibility = $allowToSeeMyPosition;
			}
			else
			{
				$friendship->friend2Visibility = $allowToSeeMyPosition;
			}
		
			if($friendship->save())
			{
				//Privacy setting saved	
				$errorOccured = false;
			}
		}

		return $errorOccured;
	}
	
	public function deleteFriendShip($friendShipId, $id) 
	{
		//TODO: use delete function not findByPk and then delete
		$friendShip = Friends::model()->findByPk($friendShipId, array('condition'=>'friend1=:friend1 OR
				friend2=:friend2',
				'params'=>array(':friend1'=>$id,
						':friend2'=>$id,
				),
		)
		);
		$result = false;
		if ($friendShip != null && $friendShip->delete()){
			$result = true;
		}
		return $result;
	}
	
	
	public function getFriendRequestDataProvider($userId, $pageSize) {
		// we look at the friend2 field because requester id is stored in friend1 field
		// and only friend who has been requested to be a friend can approve frienship
		$sqlCount = 'SELECT count(*)
		FROM '. Friends::model()->tableName() . ' f
		WHERE friend2 = '.$userId.'
		AND status= 0';
		
		$count = Yii::app()->db->createCommand($sqlCount)->queryScalar();
		
		/**
		 * because we use same view in listing users, we put requester field as false
		 * to make view show approve link,
		 * requester who make friend request cannot approve request
		 */
		$sql = 'SELECT u.Id as id, u.realname as Name, f.Id as friendShipId, f.status,
					false as requester
				FROM '. Friends::model()->tableName() . ' f
				LEFT JOIN ' . Users::model()->tableName() . ' u
					ON u.Id = f.friend1
				WHERE friend2='. $userId .' AND status= 0'  ;
		
		$dataProvider = new CSqlDataProvider($sql, array(
				'totalItemCount'=>$count,
				'sort'=>array(
						'attributes'=>array(
								'id', 'Name',
						),
				),
				'pagination'=>array(
						'pageSize'=>$pageSize,
				),
		));
		
		return $dataProvider;
	}
	
	public function approveFriendShip($friendShipId, $userId) 
	{
		// only friend2 can approve friendship because friend1 makes the request
		$friendShip = $this->findByPk($friendShipId, 
								array( 'condition'=>'friend2=:friend2 AND status=0',
										'params'=>array(':friend2'=>$userId,
												),
								)
							);
		$result = false;
		if ($friendShip != null)
		{
			$friendShip->status = 1;
			if ($friendShip->save()) {
				$result = true;
			}
		}
		
		return $result;
	}
	
	public function addAsFriend($requesterId, $partnerId)
	{
		$friend = new Friends();
		$friend->friend1 = $requesterId;
		$friend->friend1Visibility = 1; //default visibility setting is visible
		$friend->friend2 = $partnerId;
		$friend->friend2Visibility = 1; //default visibility setting is visible
		$friend->status = 0;
		$result = false;
		try
		{
			if ($friend->save()) {
				$result = true;
			}
		}
		catch (Exception $e)
		{
			if($e->getCode() == Yii::app()->params->duplicateEntryDbExceptionCode) //Duplicate Entry
			{
				$result = null;

			}
		}
		return $result;
	}
	
}