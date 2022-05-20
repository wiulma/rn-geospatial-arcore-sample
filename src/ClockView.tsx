import React, { useEffect, useState } from 'react'
import { StyleSheet, Text, View } from 'react-native'
import Clock from './Clock'

declare type Props = {}

export const ClockView: React.FC<Props> = () => {
  const [date, setDate] = useState<string>('')
  const [seconds, setSeconds] = useState<number>(0)

  useEffect(() => {
    Clock.getCurrentTime().then((time: string) => {
      setDate(new Date(time).toDateString())
    })
    Clock.getCurrentTimeEvents(setSeconds)
    Clock.dispatchEventEverySecond()
  }, [])

  return (
    <View style={styles.container}>
      <Text>{date}</Text>
      <Text>The seconds count is: {seconds}</Text>
    </View>
  )
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
})
