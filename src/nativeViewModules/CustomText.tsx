import React, { useRef, useImperativeHandle, useCallback } from 'react'
import {
  requireNativeComponent,
  UIManager,
  findNodeHandle,
  Platform,
} from 'react-native'

const COMPONENT_NAME = Platform.OS === 'ios' ? 'CustomTextView' : 'CustomText'
const NativeComponent = requireNativeComponent(COMPONENT_NAME)
/* @ts-ignore */
const NativeViewManager = UIManager[COMPONENT_NAME]

const PROP_TEXT = 'textProp'
const COMMAND_SET_TEXT = 'setText'
const EVENT_ON_TEXT_CHANGED = 'onTextChanged'
/* @ts-ignore */
const CustomText = ({ text, style = {}, onTextChanged }, ref) => {
  const nativeRef = useRef(null)

  /* @ts-ignore */
  const manipulateTextWithUIManager = useCallback((text) => {
    UIManager.dispatchViewManagerCommand(
      findNodeHandle(nativeRef.current),
      NativeViewManager.Commands[COMMAND_SET_TEXT],
      [text]
    )
  }, [])

  useImperativeHandle(
    ref,
    () => ({
      setText: manipulateTextWithUIManager,
    }),
    [manipulateTextWithUIManager]
  )

  return (
    <NativeComponent
      ref={nativeRef}
      /* @ts-ignore */
      style={[{ height: 200 }, style]}
      {...{
        [PROP_TEXT]: text,
        /* @ts-ignore */
        [EVENT_ON_TEXT_CHANGED]: ({ nativeEvent: { text } }) =>
          onTextChanged(text),
      }}
    />
  )
}

export default React.forwardRef(CustomText)
