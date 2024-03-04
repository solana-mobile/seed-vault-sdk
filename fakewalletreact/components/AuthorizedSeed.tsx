import { Text, View } from "react-native/types"

type Props = Readonly<{
    authToken: number
}>

export default function AuthorizedSeed({authToken}: Props) {

    return (
        <View>
            <Text>Authorized Seed: {authToken}</Text>
        </View>
    )
}
