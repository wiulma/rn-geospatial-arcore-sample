import React, { useState, useRef } from 'react'
import CustomText from './nativeViewModules/CustomText'
import { Alert, Button, View } from 'react-native'

export const SampleText = ({}) => {
  const textRef = useRef(null)

  const [text, setText] = useState('Hello Native Component!')

  const changeTextWithRef = () => {
    textRef.current?.setText(['Hello', 'Bye'][Math.floor(Math.random() + 0.5)])
  }

  return (
    <View style={{ flex: 1 }}>
      <Button
        title='Change text with direct manipulation'
        onPress={changeTextWithRef}
      />
      <CustomText
        ref={textRef}
        text={text}
        onTextChanged={(text: string) => {
          setText(text)
          Alert.alert(text)
        }}
      />
    </View>
  )
}
